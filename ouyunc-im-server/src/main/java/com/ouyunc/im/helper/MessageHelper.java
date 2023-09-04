package com.ouyunc.im.helper;

import com.alibaba.fastjson2.JSON;
import com.alibaba.ttl.threadpool.TtlExecutors;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.DeviceEnum;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.constant.enums.NetworkEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.exception.IMException;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.InnerExtraData;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.protocol.Protocol;
import com.ouyunc.im.utils.IdentityUtil;
import com.ouyunc.im.utils.SocketAddressUtil;
import com.ouyunc.im.utils.SystemClock;
import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPool;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import jodd.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * @Author fangzhenxun
 * @Description: 消息的传递/发送/读取
 **/
public class MessageHelper {

    private static final Logger log = LoggerFactory.getLogger(MessageHelper.class);

    private static final ExecutorService EVENT_EXECUTORS  = TtlExecutors.getTtlExecutorService(new DefaultEventExecutorGroup(16, ThreadFactoryBuilder.create().setNameFormat("message-send-pool-%d").get()));




    /**
     * @Author fangzhenxun
     * @Description 客户端做等待ack的队列处理 ，如果在一定时间内没有收到接收方返回的信息则重试发送信息（可能会导致重复接收，客户端需作去重处理），如果消息发送方此时离线，则会进行重试，问题不大
     * @param from 消息接收方,不会转发发到多登录设备上
     * @param packet 原始消息packet
     * @return void
     */
    public static void doQos(String from, Packet packet) {
        log.info("服务端正在回复from: {} ackPacket: {}", from, packet);
        // 异步直接发送
        MessageHelper.sendMessage(new Packet(packet.getProtocol(), packet.getProtocolVersion(), packet.getPacketId(), DeviceEnum.PC_OTHER.getValue(), NetworkEnum.OTHER.getValue(), IMServerContext.SERVER_CONFIG.getLocalHost(), MessageEnum.IM_QOS.getValue(), packet.getEncryptType(), packet.getSerializeAlgorithm(),  new Message(IMServerContext.SERVER_CONFIG.getLocalServerAddress(), from, MessageContentEnum.SERVER_QOS_ACK_CONTENT.type(), String.valueOf(packet.getPacketId()), SystemClock.now())), IdentityUtil.generalComboIdentity(from, packet.getDeviceType()));
    }


    /**
     * 发送消息给多个用户
     * @param loginUserInfos
     */
    public static void send2MultiDevices(Packet packet, List<LoginUserInfo> loginUserInfos) {
        // 转发给某个客户端的各个在线设备端
        for (LoginUserInfo loginUserInfo : loginUserInfos) {
            // 走消息传递,设置登录设备类型
            if (IMServerContext.SERVER_CONFIG.getLocalServerAddress().equals(loginUserInfo.getLoginServerAddress()) || !IMServerContext.SERVER_CONFIG.isClusterEnable()) {
                MessageHelper.sendMessage(packet, IdentityUtil.generalComboIdentity(loginUserInfo.getIdentity(), loginUserInfo.getDeviceEnum().getName()));
            } else {
                MessageHelper.deliveryMessage(packet, SocketAddressUtil.convert2SocketAddress(loginUserInfo.getLoginServerAddress()));
            }
        }
    }


    /**
     * @Author fangzhenxun
     * @Description 异步发送消息
     * @param packet
     * @param to 组合后的接收者,唯一用户表示，手机号，身份证号码，token，邮箱
     * @return void
     */
    public static void sendMessage(Packet packet, String to) {
        // 异步提交
        EVENT_EXECUTORS.execute(() -> sendMessageSync(packet, to));
    }

    /**
     * @Author fangzhenxun
     * @Description 同步发送消息
     * @param packet
     * @param to 接收者
     * @return void
     */
    public static void sendMessageSync(Packet packet, String to) {
        log.info("开始给 {} 发送消息packet: {} ", to, packet);
        if (to == null) {
            throw new IMException("消息接收者不能为空！");
        }
        // 判断是什么类型的协议packet,然后交给具体的协议去处理
        Message message = (Message) packet.getMessage();
        if (message.getExtra() != null) {
            ExtraMessage extraMessage = JSON.parseObject(message.getExtra(), ExtraMessage.class);
            String extra = extraMessage.getOutExtraData();
            if (extra == null) {
                extra = message.getExtra();
            }
            message.setExtra(extra);
        }
        Protocol.prototype(packet.getProtocol(), packet.getProtocolVersion()).doSendMessage(packet, to);
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            EVENT_EXECUTORS.execute(new Runnable() {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().getName());
                }
            });
        }

    }
        /**
         * @param toSocketAddress
         * @param packet
         * @return void
         * @Author fangzhenxun
         * @Description 异步  传递消息，根据服务端的ip包装成InetSocketAddress
         */
    public static void deliveryMessage(Packet packet, InetSocketAddress toSocketAddress) {
        EVENT_EXECUTORS.execute(() -> deliveryMessageSync(packet, toSocketAddress));
    }

    /**
     * @param toSocketAddress  目标服务器地址
     * @param packet 消息包
     * @return void
     * @Author fangzhenxun
     * @Description 同步传递消息，根据服务端的ip包装成InetSocketAddress
     */
    public static void deliveryMessageSync(Packet packet,InetSocketAddress toSocketAddress) {
        // 成功才将上个路由地址改成本地，异常会在异常处理中获取上个路由服务地址及设置
        // 获取消息扩展消息
        Message message = (Message) packet.getMessage();
        ExtraMessage extraMessage = JSON.parseObject(message.getExtra(), ExtraMessage.class);
        InnerExtraData innerExtraData;
        if (extraMessage == null) {
            extraMessage = new ExtraMessage();
            innerExtraData = new InnerExtraData();
        }else {
            innerExtraData = extraMessage.getInnerExtraData();
            if (innerExtraData == null) {
                innerExtraData = new InnerExtraData();
            }
        }
        // 判断是否是首次在集群间传递消息
        if (!innerExtraData.isDelivery()) {
            // 首次进行传递时，将目标主机和所登录的设备进行设置
            innerExtraData.setDeviceEnum(DeviceEnum.getDeviceEnumByValue(packet.getDeviceType()));
            innerExtraData.setTargetServerAddress(SocketAddressUtil.convert2HostPort(toSocketAddress));
            innerExtraData.setDelivery(true);
            extraMessage.setOutExtraData(message.getExtra());
            extraMessage.setInnerExtraData(innerExtraData);
            message.setExtra(JSON.toJSONString(extraMessage));
        }
        log.info("正在投递消息packet: {} 到服务: {} 上 ...",packet, toSocketAddress);
        // 将本机地址作为上一个路由服务地址传递过去
        // 先从存活的注册表中查找（防止有新添加集群中的服务），然后再从全局中找到最近的服务;
        // 重要！重要！重要！，这里是从channel pool 池中或的的channel(该channel的pipline 是内部协议的处理链，也就是说通过池中拿到的channel 所发送的消息，无论协议类型是什么都只会走内部的协议处理器，与协议类型无关),
        ChannelPool channelPool = IMServerContext.CLUSTER_ACTIVE_SERVER_REGISTRY_TABLE.get(toSocketAddress);
        // 如果从存活的服务注册表中获取不到channelPool 则进行路由其他服务去达到消息目的
        if (channelPool == null) {
            // do something
            // 找不到有以下两种情况：
            // 1,消息接收端是不在集群中的服务（非法的服务地址）,不予考虑;
            // 2,消息接收端是后来加入的集群中的服务，在旧的集群中可能由于部分服务之间网络不通导致没有该服务记录保存; 此时的处理方式是路由到其他可用服务上处理
            // 3,两个服务不直接连通，须通过中间服务做中转
            log.warn("获取不到消息需要到达的服务: {}",SocketAddressUtil.convert2HostPort(toSocketAddress));
            exceptionHandle(packet, toSocketAddress);
            return;
        }
        // 异步获取 channel
        Future<Channel> channelFuture = channelPool.acquire();
        InnerExtraData finalInnerExtraData = innerExtraData;
        ExtraMessage finalExtraMessage = extraMessage;
        channelFuture.addListener(new FutureListener<Channel>(){
            @Override
            public void operationComplete(Future<Channel> future) throws Exception {
                if (future.isDone()) {
                    // 判断是否连接成功
                    if (future.isSuccess()) {
                        Channel channel = future.getNow();
                        // 给该通道打上标签(如果该通道channel 上有标签则不需要再打标签),打上标签的目的，是为了以后动态回收该channel,保证核心channel数
                        AttributeKey<Integer> channelTagPoolKey = AttributeKey.valueOf(IMConstant.CHANNEL_TAG_POOL);
                        final Integer channelPoolHashCode = channel.attr(channelTagPoolKey).get();
                        if (channelPoolHashCode == null) {
                            channel.attr(channelTagPoolKey).set(channelPool.hashCode());
                        }
                        // 当获取channel 成功的时候才将from进行设置进去
                        finalInnerExtraData.setFromServerAddress(IMServerContext.SERVER_CONFIG.getLocalServerAddress());
                        finalExtraMessage.setInnerExtraData(finalInnerExtraData);
                        message.setExtra(JSON.toJSONString(finalExtraMessage));
                        // 客户端将数据写出到中介管道中
                        channel.writeAndFlush(packet);
                        // 用完后进行释放掉
                        channelPool.release(channel);
                    }else {
                        // 获取失败
                        Throwable cause = future.cause();
                        log.warn("客户端获取channel异常！原因: {}", cause.getMessage());
                        // 重新封装路由表信息
                        // do something
                        // 重新选择一个新的集群中的服务去路由，直到找到通的或没有任何一个连通的结束
                        exceptionHandle(packet, toSocketAddress);
                    }

                }
            }
        });
    }




    /**
     * @Author fangzhenxun
     * @Description 异常数据的处理
     * @param packet
     * @param toSocketAddress
     * @return void
     */
    private static void exceptionHandle(Packet packet, InetSocketAddress toSocketAddress) {
        // 通过路由助手，找到一个可用的服务连接，如果找不到最后会这里处理，重试，下线，等操作
        InetSocketAddress availableSocketAddress = RouterHelper.route(packet, toSocketAddress);
        if (availableSocketAddress == null) {
            return;
        }
        // 重新传递消息
        deliveryMessage(packet,availableSocketAddress);
    }

}
