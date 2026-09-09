package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.*;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.core.intercept.AbstractMessageInterceptor;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPool;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Objects;

/**
 * @Author fzx
 * @Description: 消息路由/拦截器/集群投递。已知 Channel 上的协议转换与写出见 {@link PacketChannelWriter}。
 **/
public class MessageHelper {

    private static final Logger log = LoggerFactory.getLogger(MessageHelper.class);

    /**
     * 同步发送消息给多个客户端
     */
    public static void syncSendMessage(Packet packet, Collection<LoginClientInfo> loginClientInfos) {
        for (LoginClientInfo loginClientInfo : loginClientInfos) {
            syncSendMessage(packet.clone(), buildTarget(loginClientInfo));
        }
    }

    /**
     * 异步发送消息给多个客户端
     * 注意！注意！注意！，异步发送，只是逻辑处理事异步的，但是具体讲消息发送出去的时间不确定，因为最后发送消息的的writeAndFlush()方法，会被封装到channel.eventLoop()单线程的任务队列中；队列里面任务的执行时间可查看相关文档
     */
    public static void asyncSendMessage(Packet packet, Collection<LoginClientInfo> loginClientInfos) {
        for (LoginClientInfo loginClientInfo : loginClientInfos) {
            asyncSendMessage(packet.clone(), buildTarget(loginClientInfo));
        }
    }

    public static Target buildTarget(LoginClientInfo loginClientInfo) {
        return Target.newBuilder()
                .appKey(loginClientInfo.getAppKey())
                .targetIdentity(loginClientInfo.getIdentity())
                .targetServerAddress(loginClientInfo.getLoginServerAddress())
                .deviceType(loginClientInfo.getDeviceType())
                .protocol(loginClientInfo.getProtocol())
                .protocolVersion(loginClientInfo.getProtocolVersion())
                .build();
    }

    /**
     * @Author fzx
     * @Description 同步发送消息
     */
    public static void syncSendMessage(Packet packet, Target target) {
        if (CollectionUtils.isEmpty(MessageServerContext.messageInterceptorChain)) {
            doSendMessage(packet, target, (sendResult)->{});
            return;
        }
        try {
            for (AbstractMessageInterceptor messageInterceptor : MessageServerContext.messageInterceptorChain) {
                if (!messageInterceptor.preHandle(packet, target)) {
                    log.debug("消息拦截器 {} 拦截了消息: {}", messageInterceptor.getClass().getName(), packet);
                    return;
                }
            }
            doSendMessage(packet, target, (sendResult)->{});
            for (AbstractMessageInterceptor messageInterceptor : MessageServerContext.messageInterceptorChain) {
                messageInterceptor.postHandle(packet, target);
            }
        } catch (Exception e) {
            log.error("同步发送消息过程中发生异常", e);
        }
    }

    /**
     * @Author fzx
     * @Description 同步发送消息，不尝试使用拦截器
     */
    public static void syncSendMessageWithoutInterceptor(Packet packet, Target target) {
        doSendMessage(packet, target, (sendResult)->{});
    }


    /**
     * @Author fzx
     * @Description 同步发送消息，不尝试使用拦截器
     */
    public static void syncSendMessageWithoutInterceptor(Packet packet, Target target, SendCallback sendCallback) {
        doSendMessage(packet, target, sendCallback);
    }


    /**
     * @Author fzx
     * @Description 异步发送消息，不带回调，
     * 注意！注意！注意！，异步发送，只是逻辑处理事异步的，但是具体讲消息发送出去的时间不确定，因为最后发送消息的的writeAndFlush()方法，会被封装到channel.eventLoop()单线程的任务队列中；队列里面任务的执行时间可查看相关文档
     */
    public static void asyncSendMessage(Packet packet, Target target) {
        asyncSendMessage(packet, target, (sendResult)->{});
    }



    /**
     * @Author fzx
     * @Description 异步投递消息，添加回调，不尝试使用拦截器
     * 注意！注意！注意！，异步发送，只是逻辑处理事异步的，但是具体讲消息发送出去的时间不确定，因为最后发送消息的的writeAndFlush()方法，会被封装到channel.eventLoop()单线程的任务队列中；队列里面任务的执行时间可查看相关文档
     */
    public static void asyncSendMessageWithoutInterceptor(Packet packet, Target target) {
        ThreadPoolManager.messageSendExecutor().execute(()-> {
            doSendMessage(packet, target, (sendResult)->{});
        });
    }

    /**
     * @Author fzx
     * @Description 异步投递消息，添加回调，不尝试使用拦截器
     * 注意！注意！注意！，异步发送，只是逻辑处理事异步的，但是具体讲消息发送出去的时间不确定，因为最后发送消息的的writeAndFlush()方法，会被封装到channel.eventLoop()单线程的任务队列中；队列里面任务的执行时间可查看相关文档
     */
    public static void asyncSendMessageWithoutInterceptor(Packet packet, Target target, SendCallback sendCallback) {
        ThreadPoolManager.messageSendExecutor().execute(()-> {
            doSendMessage(packet, target, sendCallback);
        });
    }


    /**
     * @Author fzx
     * @Description 异步投递消息，添加回调
     * 注意！注意！注意！，异步发送，只是逻辑处理事异步的，但是具体讲消息发送出去的时间不确定，因为最后发送消息的的writeAndFlush()方法，会被封装到channel.eventLoop()单线程的任务队列中；队列里面任务的执行时间可查看相关文档
     */
    private static void asyncSendMessage(Packet packet, Target target, SendCallback sendCallback) {
        ThreadPoolManager.messageSendExecutor().execute(()-> {
            if (CollectionUtils.isEmpty(MessageServerContext.messageInterceptorChain)) {
                doSendMessage(packet, target, sendCallback);
                return;
            }
            try {
                for (AbstractMessageInterceptor messageInterceptor : MessageServerContext.messageInterceptorChain) {
                    if (!messageInterceptor.preHandle(packet, target)) {
                        log.debug("消息拦截器 {} 拦截了消息: {}", messageInterceptor.getClass().getName(), packet);
                        return;
                    }
                }
                doSendMessage(packet, target, sendCallback);
                for (AbstractMessageInterceptor messageInterceptor : MessageServerContext.messageInterceptorChain) {
                    messageInterceptor.postHandle(packet, target);
                }
            } catch (Exception e) {
                log.error("同步发送消息过程中发生异常", e);
            }
        });
    }



    /**
     * 同步投递。集群中 {@link Target#getTargetServerAddress()} 是最终落地机，不可改成下一跳。
     */
    private static void doSendMessage(Packet originPacket, Target target, SendCallback sendCallback) {
        Metadata originMetadata = originPacket.getMessage().getMetadata();
        originMetadata.setTarget(target);
        String destServerAddress = target.getTargetServerAddress();
        String localServerAddress = MessageServerContext.serverProperties().getLocalServerAddress();
        if (!MessageServerContext.serverProperties().isClusterEnable()
                || Objects.equals(localServerAddress, destServerAddress)) {
            deliverLocal(originPacket, target, sendCallback);
            return;
        }
        Packet packet = originPacket.clone();
        Metadata metadata = packet.getMessage().getMetadata();
        if (!metadata.isRouted()) {
            metadata.setRouted(true);
        }
        ChannelPool destPool = resolveClusterChannelPool(destServerAddress);
        if (destPool != null) {
            writeViaClusterPool(packet, destPool, destServerAddress, sendCallback);
            return;
        }
        log.warn("获取不到消息需要到达的服务: {}，尝试经其他节点中转，最终 dest 不变", destServerAddress);
        relayViaNextHop(packet, destServerAddress, sendCallback);
    }

    /**
     * 本机落地：普通单播按 identity+device 写连接；节点广播只扫本机登录表。
     */
    private static void deliverLocal(Packet packet, Target target, SendCallback sendCallback) {
        Metadata metadata = packet.getMessage().getMetadata();
        String identity = target.getTargetIdentity();
        if (metadata != null && metadata.isLocalBroadcastOnly()
                && (identity == null || identity.isEmpty())) {
            ClientHelper.deliverLocalBroadcast(target.getAppKey(), packet);
            return;
        }
        MessageServerContext.findProtocol(target.getProtocol(), target.getProtocolVersion())
                .doSendMessage(packet, IdentityUtil.generalComboIdentity(
                        target.getAppKey(), target.getTargetIdentity(), target.getDeviceType()), sendCallback);
    }

    /**
     * 先 active 再 global。池中 channel 走内部协议 pipeline，与客户端协议类型无关。
     */
    private static ChannelPool resolveClusterChannelPool(String serverAddress) {
        ChannelPool channelPool = MessageServerContext.clusterActiveServerRegistryTableCache.get(serverAddress);
        if (channelPool != null) {
            return channelPool;
        }
        return MessageServerContext.clusterGlobalServerRegistryTableCache.get(serverAddress);
    }

    /**
     * 直连 dest 或 hop 失败：route(失败地址) 只选下一跳连接，包上 dest 保持登录机/目标节点。
     */
    private static void relayViaNextHop(Packet packet, String failedServerAddress, SendCallback sendCallback) {
        String nextHop = MessageServerContext.messageRouter.route(packet, failedServerAddress);
        if (nextHop == null) {
            notifySendFail(packet, "消息id: " + packet.getPacketId() + " 尝试路由多次，都没有找到可用的服务！", sendCallback);
            return;
        }
        ChannelPool hopPool = resolveClusterChannelPool(nextHop);
        if (hopPool == null) {
            log.warn("下一跳 {} 无连接池，继续回溯", nextHop);
            relayViaNextHop(packet, nextHop, sendCallback);
            return;
        }
        writeViaClusterPool(packet, hopPool, nextHop, sendCallback);
    }

    /**
     * 经 hop 的集群连接写出；acquire 失败则对该 hop 再回溯，不改 Target.targetServerAddress。
     */
    private static void writeViaClusterPool(Packet packet, ChannelPool channelPool,
                                            String hopServerAddress, SendCallback sendCallback) {
        Future<Channel> channelFuture = channelPool.acquire();
        if (channelFuture == null) {
            log.warn("获取不到消息需要到达的服务连接: {}", hopServerAddress);
            relayViaNextHop(packet, hopServerAddress, sendCallback);
            return;
        }
        Metadata metadata = packet.getMessage().getMetadata();
        channelFuture.addListener((FutureListener<Channel>) acquireFuture -> {
            if (!acquireFuture.isDone()) {
                log.error("发送消息时，获取channel异常！");
                return;
            }
            if (!acquireFuture.isSuccess()) {
                Throwable cause = acquireFuture.cause();
                log.warn("客户端获取channel异常！原因: {}", cause == null ? "" : cause.getMessage());
                relayViaNextHop(packet, hopServerAddress, sendCallback);
                return;
            }
            Channel channel = acquireFuture.getNow();
            if (channel == null) {
                log.error("发送集群消息时，获取channel失败！");
                notifySendFail(packet, "发送集群消息时，获取channel失败！", sendCallback);
                return;
            }
            Integer channelPoolHashCode = ChannelAttrUtil.getChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_TAG_POOL);
            if (channelPoolHashCode == null) {
                ChannelAttrUtil.setChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_TAG_POOL, channelPool.hashCode());
            }
            metadata.setFromServerAddress(MessageServerContext.serverProperties().getLocalServerAddress());
            Runnable releaseChannel = () -> channelPool.release(channel);
            PacketChannelWriter.runOnEventLoop(channel, packet, sendCallback,
                    () -> PacketChannelWriter.tryWritePacketAndThen(channel, packet, sendCallback, releaseChannel),
                    releaseChannel);
        });
    }


    /** 发送失败回调与事件，实现见 {@link PacketChannelWriter#notifySendFail}。 */
    public static void notifySendFail(Packet packet, Throwable cause, SendCallback sendCallback) {
        PacketChannelWriter.notifySendFail(packet, cause, sendCallback);
    }

    public static void notifySendFail(Packet packet, String message, SendCallback sendCallback) {
        PacketChannelWriter.notifySendFail(packet, message, sendCallback);
    }

    /** MQTT 控制报文等已转换对象写出，实现见 {@link PacketChannelWriter#tryWriteObject}。 */
    public static boolean tryWriteObject(Channel channel, Object msg, Packet packet, SendCallback sendCallback) {
        return PacketChannelWriter.tryWriteObject(channel, msg, packet, sendCallback);
    }

}
