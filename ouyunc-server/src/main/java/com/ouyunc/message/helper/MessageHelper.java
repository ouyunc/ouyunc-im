package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.SendStatusEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.*;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.core.intercept.AbstractMessageInterceptor;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoop;
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
 * @Description: 消息的传递/发送/读取
 **/
public class MessageHelper {

    private static final Logger log = LoggerFactory.getLogger(MessageHelper.class);

    /**
     * 同步发送消息给多个客户端
     */
    public static void syncSendMessage(Packet packet, Collection<LoginClientInfo> loginClientInfos) {
        // 转发给某个客户端的各个在线设备端
        for (LoginClientInfo loginClientInfo : loginClientInfos) {
            // 走消息传递,设置登录设备类型
            syncSendMessage(packet, Target.newBuilder().targetIdentity(loginClientInfo.getIdentity()).targetServerAddress(loginClientInfo.getLoginServerAddress()).deviceType(loginClientInfo.getDeviceType()).protocol(loginClientInfo.getProtocol()).protocolVersion(loginClientInfo.getProtocolVersion()).build());
        }
    }

    /**
     * 异步发送消息给多个客户端
     * 注意！注意！注意！，异步发送，只是逻辑处理事异步的，但是具体讲消息发送出去的时间不确定，因为最后发送消息的的writeAndFlush()方法，会被封装到channel.eventLoop()单线程的任务队列中；队列里面任务的执行时间可查看相关文档
     */
    public static void asyncSendMessage(Packet packet, Collection<LoginClientInfo> loginClientInfos) {
        // 转发给某个客户端的各个在线设备端
        for (LoginClientInfo loginClientInfo : loginClientInfos) {
            // 走消息传递,设置登录设备类型
            asyncSendMessage(packet, Target.newBuilder().targetIdentity(loginClientInfo.getIdentity()).targetServerAddress(loginClientInfo.getLoginServerAddress()).deviceType(loginClientInfo.getDeviceType()).protocol(loginClientInfo.getProtocol()).protocolVersion(loginClientInfo.getProtocolVersion()).build());
        }
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
     * @Author fzx
     * @Description 同步投递消息,不对外暴漏
     */
    private static void doSendMessage(Packet originPacket, Target target, SendCallback sendCallback) {
        log.debug("开始给 {} 传递消息packet: {} ", target, originPacket);
        // 设置target,只设置一次
        Metadata originMetadata = originPacket.getMessage().getMetadata();
        if (originMetadata.getTarget() == null) {
            originMetadata.setTarget(target);
        }
        // 需要发送到的服务器地址
        String toServerAddress = target.getTargetServerAddress();
        // 如果是单服务实例或者如果目标主机是本机，则直接发送处理
        if (!MessageServerContext.serverProperties().isClusterEnable() || Objects.equals(MessageServerContext.serverProperties().getLocalServerAddress(), toServerAddress)) {
            MessageServerContext.findProtocol(target.getProtocol(), target.getProtocolVersion()).doSendMessage(originPacket, IdentityUtil.generalComboIdentity(originPacket.getMessage().getMetadata().getAppKey(), target.getTargetIdentity(), target.getDeviceType()), sendCallback);
            return;
        }
        Packet packet = originPacket.clone();
        // 获取消息元数据消息
        Metadata metadata = packet.getMessage().getMetadata();
        // 判断是否是首次在集群间传递消息
        if (!metadata.isRouted()) {
            // 首次进行传递时，将目标以及目标主机和所登录的设备进行设置
            metadata.setRouted(true);
        }
        // 将本机地址作为上一个路由服务地址传递过去
        // 先从存活的注册表中查找（防止有新添加集群中的服务），然后再从全局中找到最近的服务;
        // 重要！重要！重要！，这里是从channel pool 池中获取的channel(该channel的pipline 是内部协议的处理链，也就是说通过池中拿到的channel 所发送的消息，无论协议类型是什么都只会走内部的协议处理器，与协议类型无关),
        ChannelPool channelPool = MessageServerContext.clusterActiveServerRegistryTableCache.get(toServerAddress);
        // 如果从存活的服务注册表中获取不到channelPool 则进行路由其他服务去达到消息目的
        if (channelPool == null) {
            // 找不到有以下两种情况：
            // 1,消息接收端是不在集群中的服务（非法的服务地址）,不予考虑;
            // 2,消息接收端是后来加入的集群中的服务，在旧的集群中可能由于部分服务之间网络不通导致没有该服务记录保存; 此时的处理方式是路由到其他可用服务上处理
            // 3,两个服务不直接连通，须通过中间服务做中转
            log.warn("获取不到消息需要到达的服务: {}", toServerAddress);
            exceptionHandle(packet, target, sendCallback);
            return;
        }
        // 异步获取 channel
        Future<Channel> channelFuture = channelPool.acquire();
        if (channelFuture == null) {
            // 找不到有以下两种情况：
            // 1,消息接收端是不在集群中的服务（非法的服务地址）,不予考虑;
            // 2,消息接收端是后来加入的集群中的服务，在旧的集群中可能由于部分服务之间网络不通导致没有该服务记录保存; 此时的处理方式是路由到其他可用服务上处理
            // 3,两个服务不直接连通，须通过中间服务做中转
            log.warn("获取不到消息需要到达的服务: {}", toServerAddress);
            exceptionHandle(packet, target, sendCallback);
            return;
        }
        // 监听是否发送成功
        channelFuture.addListener((FutureListener<Channel>) acquireFuture -> {
            if (acquireFuture.isDone()) {
                // 判断是否连接成功
                if (acquireFuture.isSuccess()) {
                    Channel channel = acquireFuture.getNow();
                    if (channel == null) {
                        log.error("发送集群消息时，获取channel失败！");
                        // 发送结果
                        SendResult sendResult = SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(new MessageException("发送集群消息时，获取channel失败！")).build();
                        // 发送失败回调
                        sendCallback.onCallback(sendResult);
                        // 发布发送失败事件
                        MessageServerContext.publishEvent(new MessageEvent(sendResult, MessageEventTypeEnum.SEND_FAIL), true);
                        return;
                    }

                    // 给该通道打上标签(如果该通道channel 上有标签则不需要再打标签),打上标签的目的，是为了以后动态回收该channel,保证核心channel数
                    Integer channelPoolHashCode = ChannelAttrUtil.getChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_TAG_POOL);
                    if (channelPoolHashCode == null) {
                        ChannelAttrUtil.setChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_TAG_POOL, channelPool.hashCode());
                    }
                    // 当获取channel 成功的时候才将from进行设置进去
                    metadata.setFromServerAddress(MessageServerContext.serverProperties().getLocalServerAddress());
                    // 客户端将数据写出到中介管道中
                    EventLoop eventLoop = channel.eventLoop();
                    Runnable releaseChannel = () -> channelPool.release(channel);
                    if (eventLoop.inEventLoop()) {
                        tryWritePacketAndThen(channel, packet, sendCallback, releaseChannel);
                    } else if (!eventLoop.isTerminated() && !eventLoop.isShutdown() && !eventLoop.isShuttingDown()) {
                        eventLoop.execute(() ->  tryWritePacketAndThen(channel, packet, sendCallback, releaseChannel));
                    }else {
                        releaseChannel.run();
                        log.error("发送消息时，channel.eventLoop 被终止或关闭！");
                        // 发送结果
                        SendResult sendResult = SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(new MessageException("集群间发送消息时，channel.eventLoop 被终止或关闭!")).build();
                        // 发送失败回调
                        sendCallback.onCallback(sendResult);
                        // 发布发送失败事件
                        MessageServerContext.publishEvent(new MessageEvent(sendResult, MessageEventTypeEnum.SEND_FAIL), true);
                    }
                } else {
                    // 获取失败
                    Throwable cause = acquireFuture.cause();
                    log.warn("客户端获取channel异常！原因: {}", cause.getMessage());
                    // 重新选择一个新的集群中的服务去路由，直到找到通的或没有任何一个连通的结束
                    exceptionHandle(packet, target, sendCallback);
                }
            }else {
                log.error("发送消息时，获取channel异常！");
            }
        });
    }


    /**
     * 与连接池配合：写入完成后执行 afterComplete（用于归还 Channel 等）。
     */
    public static void tryWritePacketAndThen(Channel channel, Packet packet, SendCallback sendCallback, Runnable afterComplete) {
        if (!validateWritable(channel, packet, sendCallback)) {
            if (afterComplete != null) {
                afterComplete.run();
            }
            return;
        }
        ChannelFuture future = channel.writeAndFlush(packet);
        addWriteListener(future, packet, sendCallback);
        if (afterComplete != null) {
            future.addListener(f -> afterComplete.run());
        }
    }


    /**
     * 对任意出站对象执行写入（如已转换的 WS 帧），带背压检查与回调。
     */
    public static boolean tryWriteObject(Channel channel, Object msg, Packet packet, SendCallback sendCallback) {
        if (!validateWritable(channel, packet, sendCallback)) {
            return false;
        }
        addWriteListener(channel.writeAndFlush(msg), packet, sendCallback);
        return true;
    }

    /**
     * 通用校验：通道可用且可写
     */
    private static boolean validateWritable(Channel channel, Packet packet, SendCallback sendCallback) {
        if (channel == null) {
            SendResult sendResult = SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(new MessageException("channel 为空，无法写入")).build();
            sendCallback.onCallback(sendResult);
            MessageServerContext.publishEvent(new MessageEvent(sendResult, MessageEventTypeEnum.SEND_FAIL), true);
            return false;
        }
        if (!channel.isActive()) {
            SendResult sendResult = SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(new MessageException("channel 未激活，无法写入")).build();
            sendCallback.onCallback(sendResult);
            MessageServerContext.publishEvent(new MessageEvent(sendResult, MessageEventTypeEnum.SEND_FAIL), true);
            return false;
        }
        if (!channel.isWritable()) {
            log.warn("channel 不可写，丢弃或等待上层重试: {}", channel);
            SendResult sendResult = SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(new MessageException("channel 当前不可写")).build();
            sendCallback.onCallback(sendResult);
            MessageServerContext.publishEvent(new MessageEvent(sendResult, MessageEventTypeEnum.SEND_FAIL), true);
            return false;
        }
        return true;
    }

    /**
     * 统一写入回调封装
     */
    private static void addWriteListener(ChannelFuture future, Packet packet, SendCallback sendCallback) {
        future.addListener((ChannelFutureListener) f -> {
            if (f.isSuccess()) {
                sendCallback.onCallback(SendResult.builder().sendStatus(SendStatusEnum.SEND_OK).packet(packet).build());
            } else {
                SendResult sendResult = SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(f.cause()).build();
                sendCallback.onCallback(sendResult);
                MessageServerContext.publishEvent(new MessageEvent(sendResult, MessageEventTypeEnum.SEND_FAIL), true);
            }
        });
    }


    /**
     * @Author fzx
     * @Description 异常数据的处理
     */
    private static void exceptionHandle(Packet packet, Target target, SendCallback sendCallback) {
        // 通过路由助手，找到一个可用的服务连接，如果找不到最后会这里处理，重试，下线，等操作
        String nextAvailableSocketAddress = MessageServerContext.messageRouter.route(packet, target.getTargetServerAddress());
        if (nextAvailableSocketAddress == null) {
            // 发送结果
            SendResult sendResult = SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(new MessageException("消息id: " + packet.getPacketId() + " 尝试路由多次，都没有找到可用的服务！")).build();
            // 发送失败回调
            sendCallback.onCallback(sendResult);
            // 发布发送失败事件
            MessageServerContext.publishEvent(new MessageEvent(sendResult, MessageEventTypeEnum.SEND_FAIL), true);
            return;
        }
        // 设置可用的下个目标服务
        target.setTargetServerAddress(nextAvailableSocketAddress);
        doSendMessage(packet, target, sendCallback);
    }
}
