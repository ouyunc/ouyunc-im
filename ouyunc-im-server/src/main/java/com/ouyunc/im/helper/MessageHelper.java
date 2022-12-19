package com.ouyunc.im.helper;

import cn.hutool.json.JSONUtil;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.base.RoutingTable;
import com.ouyunc.im.exception.IMException;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.protocol.Protocol;
import com.ouyunc.im.utils.SocketAddressUtil;
import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPool;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author fangzhenxun
 * @Description: 消息的传递/发送/读取
 * @Version V3.0
 **/
public class MessageHelper {

    private static Logger log = LoggerFactory.getLogger(MessageHelper.class);

    private static final EventExecutorGroup eventExecutors= new DefaultEventExecutorGroup(16);


    /**
     * @Author fangzhenxun
     * @Description 异步发送消息
     * @param packet
     * @param to 接收者,唯一用户表示，手机号，身份证号码，token，邮箱
     * @return void
     */
    public static void sendMessage(Packet packet, String to) {
        // 异步提交
        eventExecutors.execute(() -> sendMessageSync(packet, to));
    }

    /**
     * @Author fangzhenxun
     * @Description 同步发送消息
     * @param packet
     * @param to 接收者
     * @return void
     */
    public static void sendMessageSync(Packet packet, String to) {
        log.info("开始发送消息packet: {} ,to: {}", packet, to);
        if (to == null) {
            throw new IMException("消息接收者不能为空！");
        }
        // 判断是什么类型的协议packet,然后交给具体的协议去处理
        Protocol.prototype(packet.getProtocol(), packet.getProtocolVersion()).doSendMessage(packet, to);
    }


        /**
         * @param toSocketAddress
         * @param packet
         * @return void
         * @Author fangzhenxun
         * @Description 异步  传递消息，根据服务端的ip包装成InetSocketAddress
         */
    public static void deliveryMessage(Packet packet, InetSocketAddress toSocketAddress) {
        eventExecutors.execute(() -> deliveryMessageSync(packet, toSocketAddress));
    }

    /**
     * @param toSocketAddress  目标服务器地址
     * @param packet 消息包
     * @return void
     * @Author fangzhenxun
     * @Description 同步传递消息，根据服务端的ip包装成InetSocketAddress
     */
    public static void deliveryMessageSync(Packet packet,InetSocketAddress toSocketAddress) {
        log.info("正在传递消息packet: {} ...",packet);
        // 将本机地址作为上一个路由服务地址传递过去
        // 先从存活的注册表中查找（防止有新添加集群中的服务），然后再从全局中找到最近的服务;
        ChannelPool channelPool = IMServerContext.CLUSTER_ACTIVE_SERVER_REGISTRY_TABLE.get(toSocketAddress);
        // 如果从激活的服务注册表中获取不到channelPool 则进行路由其他服务去达到消息目的
        if (channelPool == null) {
            // do something
            // 找不到有以下两种情况：
            // 1,消息接收端是不在集群中的服务（非法的服务地址）,不予考虑;
            // 2,消息接收端是后来加入的集群中的服务，在旧的集群中可能由于部分服务之间网络不通导致没有该服务记录保存; 此时的处理方式是路由到其他可用服务上处理
            // 3,两个服务不直接连通，须通过中间服务做中转
            log.warn("获取不到消息需要到达的服务: {}",SocketAddressUtil.convert2HostPort(toSocketAddress));
            exceptionHandle(toSocketAddress, packet);
            return;
        }
        // 异步获取 channel
        Future<Channel> channelFuture = channelPool.acquire();
        channelFuture.addListener(new FutureListener<Channel>(){
            @Override
            public void operationComplete(Future<Channel> future) throws Exception {
                if (future.isDone()) {
                    // 判断是否连接成功
                    if (future.isSuccess()) {
                        // 成功才将上个路由地址改成本地，异常会在异常处理中获取上个路由服务地址及设置
                        // 获取消息扩展消息
                        Message message = (Message) packet.getMessage();
                        ExtraMessage extraMessage = JSONUtil.toBean(message.getExtra(), ExtraMessage.class);
                        if (extraMessage == null) {
                            extraMessage = new ExtraMessage();
                        }
                        // 每次调用都会走这一步进行设置为true
                        if (!extraMessage.isDelivery()) {
                            extraMessage.setDelivery(true);
                        }
                        extraMessage.setFromServerAddress(IMServerContext.SERVER_CONFIG.getLocalServerAddress());
                        //@todo 检测是否自动设置到packet
                        message.setExtra(JSONUtil.toJsonStr(extraMessage));

                        Channel channel = future.getNow();
                        // 给该通道打上标签(如果该通道channel 上有标签则不需要再打标签),打上标签的目的，是为了以后动态回收该channel,保证核心channel数
                        AttributeKey<Integer> channelTagPoolKey = AttributeKey.valueOf(IMConstant.CHANNEL_TAG_POOL);
                        final Integer channelPoolHashCode = channel.attr(channelTagPoolKey).get();
                        if (channelPoolHashCode == null) {
                            channel.attr(channelTagPoolKey).set(channelPool.hashCode());
                        }
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
                        exceptionHandle(toSocketAddress, packet);
                    }

                }
            }
        });
    }


    /**
     * @Author fangzhenxun
     * @Description 解析后再包装消息,也就是在消息中添加相关数据, 返回包装后的消息已经路由不通的服务列表
     * // 需要根据序列化方式取出对应的服务地址
     * @param toSocketAddress
     * @param extraMessage
     * @return void
     */
    public static Set<String> wrapperMessage(InetSocketAddress toSocketAddress,ExtraMessage extraMessage) {
        List<RoutingTable> routingTables = extraMessage.routingTables();
        // 本地地址
        String toServerAddress = SocketAddressUtil.convert2HostPort(toSocketAddress);
        // 目标地址,这个方法是通用的
        // 如果目标机地址与下一个路由服务的地址相同则添加本地socketAddress 到消息中，否则添加toSocketAddress
        if (toServerAddress.equals(extraMessage.getTargetServerAddress())) {
            routingTables.add(new RoutingTable(IMServerContext.SERVER_CONFIG.getLocalServerAddress()));
        } else {
            routingTables.add(new RoutingTable(toServerAddress));
        }
        extraMessage.setRoutingTables(routingTables);

        // 转换set返回
        return routingTables.stream().map(routingTable -> routingTable.getServerAddress()).collect(Collectors.toSet());
    }


    /**
     * @Author fangzhenxun
     * @Description 异常数据的处理
     * @param toSocketAddress
     * @param packet
     * @return void
     */
    private static void exceptionHandle(InetSocketAddress toSocketAddress, Packet packet) {
        // 通过路由助手，找到一个可用的服务连接，如果找不到最后会这里处理，重试，下线，等操作
        InetSocketAddress availableSocketAddress = RouterHelper.route(toSocketAddress, packet);
        if (availableSocketAddress == null) {
            return;
        }
        // 重新传递消息
        deliveryMessage(packet,availableSocketAddress);
    }

}
