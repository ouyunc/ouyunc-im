package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.OuyuncMessageTypeEnum;
import com.ouyunc.base.constant.enums.SendStatusEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.SendCallback;
import com.ouyunc.base.model.SendResult;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.pool.ChannelPool;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Author fzx
 * @Description: 消息的传递/发送/读取
 **/
public class MessageHelper {

    private static final Logger log = LoggerFactory.getLogger(MessageHelper.class);

    private static final  ExecutorService messageSendExecutor= Executors.newVirtualThreadPerTaskExecutor();


    /**
     * @Author fzx
     * @Description 同步发送消息
     */
    public static void syncSendMessage(Packet packet, Target target) {
        doSendMessage(packet, target, (sendResult)->{});
    }

    /**
     * @Author fzx
     * @Description 异步发送消息，不带回调
     */
    public static void asyncSendMessage(Packet packet, Target target) {
        asyncSendMessage(packet, target, (sendResult)->{});
    }


    /**
     * @Author fzx
     * @Description 异步投递消息，添加回调
     */
    public static void asyncSendMessage(Packet packet, Target target, SendCallback sendCallback) {
        messageSendExecutor.execute(()-> doSendMessage(packet, target, sendCallback));
    }



    /**
     * @Author fzx
     * @Description 同步投递消息,不对外暴漏
     */
    private static void doSendMessage(Packet packet, Target target, SendCallback sendCallback) {
        log.info("开始给 {} 传递消息packet: {} ", target, packet);
        // 如果是单服务实例或者如果目标主机是本机，则直接发送处理
        if (!MessageServerContext.serverProperties().isClusterEnable() || MessageServerContext.serverProperties().getLocalServerAddress().equals(target.getTargetServerAddress()) || (packet.getProtocol() == NativePacketProtocol.OUYUNC.getProtocol() && packet.getMessageType() == OuyuncMessageTypeEnum.SYN_ACK.getType())) {
            MessageServerContext.findProtocol(packet.getProtocol(), packet.getProtocolVersion()).doSendMessage(packet, IdentityUtil.generalComboIdentity(target.getTargetIdentity(), target.getDeviceType()), sendCallback);
            return;
        }
        String toServerAddress = target.getTargetServerAddress();
        // 获取消息元数据消息
        Metadata metadata = packet.getMessage().getMetadata();
        // 判断是否是首次在集群间传递消息
        if (!metadata.isRouted()) {
            // 首次进行传递时，将目标以及目标主机和所登录的设备进行设置
            metadata.setRouted(true);
            metadata.setTarget(target);
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
        // 监听是否发送成功
        channelFuture.addListener((FutureListener<Channel>) acquireFuture -> {
            if (acquireFuture.isDone()) {
                // 判断是否连接成功
                if (acquireFuture.isSuccess()) {
                    Channel channel = acquireFuture.getNow();
                    // 给该通道打上标签(如果该通道channel 上有标签则不需要再打标签),打上标签的目的，是为了以后动态回收该channel,保证核心channel数
                    AttributeKey<Integer> channelTagPoolKey = AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_TAG_POOL);
                    Integer channelPoolHashCode = channel.attr(channelTagPoolKey).get();
                    if (channelPoolHashCode == null) {
                        channel.attr(channelTagPoolKey).set(channelPool.hashCode());
                    }
                    // 当获取channel 成功的时候才将from进行设置进去
                    metadata.setFromServerAddress(MessageServerContext.serverProperties().getLocalServerAddress());
                    // 客户端将数据写出到中介管道中
                    channel.writeAndFlush(packet).addListener((ChannelFutureListener) future -> {
                        if (channelFuture.isDone()) {
                            if (channelFuture.isSuccess()) {
                                sendCallback.onCallback(SendResult.builder().sendStatus(SendStatusEnum.SEND_OK).packet(packet).build());
                            }else {
                                sendCallback.onCallback(SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(future.cause()).build());
                            }
                        }
                    });;
                    // 用完后进行释放掉
                    channelPool.release(channel);
                } else {
                    // 获取失败
                    Throwable cause = acquireFuture.cause();
                    log.warn("客户端获取channel异常！原因: {}", cause.getMessage());
                    // 重新选择一个新的集群中的服务去路由，直到找到通的或没有任何一个连通的结束
                    exceptionHandle(packet, target, sendCallback);
                }

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
            sendCallback.onCallback(SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(new MessageException("消息id: " + packet.getPacketId() + " 尝试路由多次，都没有找到可用的服务！")).build());
            return;
        }
        // 设置可用的下个目标服务
        target.setTargetServerAddress(nextAvailableSocketAddress);
        asyncSendMessage(packet, target, sendCallback);
    }
}
