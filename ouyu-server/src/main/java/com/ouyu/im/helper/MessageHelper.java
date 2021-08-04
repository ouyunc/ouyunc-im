package com.ouyu.im.helper;

import cn.hutool.core.collection.CollectionUtil;
import com.ouyu.im.constant.ImConstant;
import com.ouyu.im.constant.enums.RouterStrategyEnum;
import com.ouyu.im.context.IMContext;
import com.ouyu.im.entity.RoutingTable;
import com.ouyu.im.exception.IMException;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.packet.message.Message;
import com.ouyu.im.protocol.Protocol;
import com.ouyu.im.utils.SocketAddressUtil;
import io.netty.channel.Channel;
import io.netty.channel.pool.ChannelPool;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @Author fangzhenxun
 * @Description: 消息的传递/发送/读取
 * @Version V1.0
 **/
public class MessageHelper {

    private static Logger log = LoggerFactory.getLogger(MessageHelper.class);


    /**
     * @Author fangzhenxun
     * @Description 发送消息
     * @param packet
     * @param to 接收者可能有多个
     * @return void
     */
    public static void sendMessage(Packet packet, String... to) {
        if (to == null || to.length == 0) {
            throw new IMException("消息接收者不能为空！");
        }
        Protocol.prototype(packet.getProtocol(), packet.getProtocolVersion()).doSendMessage(packet, to);
    }


        /**
         * @param toSocketAddress
         * @param packet
         * @return void
         * @Author fangzhenxun
         * @Description 根据服务端的ip包装成InetSocketAddress  然后获取某个channel，并且给获取的channel 打上tag 标记,使用过就释放该channel
         *  该方法可以单独开线程处理
         */
    public static void deliveryMessage(InetSocketAddress toSocketAddress, Packet packet) {
        Message message = (Message) packet.getMessage();
        // 先从注册表中查找（防止有新添加集群中的服务），然后再从全局中找到最近的服务;
        ChannelPool channelPool = IMContext.CLUSTER_SERVER_REGISTRY_TABLE.get(toSocketAddress);
        if (channelPool == null) {
            List<RoutingTable> routingTables = message.routingTables();
            if (CollectionUtil.isEmpty(routingTables)) {
                List<String> routedServerAddresses = new ArrayList<>();
                routedServerAddresses.add(message.getTargetServerAddress());
                // 找到上个服务
                routingTables.add(new RoutingTable(IMContext.LOCAL_ADDRESS, null, routedServerAddresses));
            }
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
        // 注册监听
        channelFuture.addListener(new FutureListener<Channel>() {
            public void operationComplete(Future<Channel> future) throws Exception {
                // 判断是否已经处理完
                if (future.isDone()) {
                    List<RoutingTable> routingTables = message.routingTables();
                    if (routingTables == null) {
                        routingTables = new ArrayList<>();
                    }
                    String toSocketAddressStr = SocketAddressUtil.convert2HostPort(toSocketAddress);

                    // 判断是否成功连接，获取channel
                    if (future.isSuccess()) {
                        Channel channel = future.getNow();
                        // 将该通道打上标签,(如果该通道channel 上有标签则不需要再打标签)
                        AttributeKey<Integer> channelTagPoolKey = AttributeKey.valueOf(ImConstant.CHANNEL_TAG_POOL);
                        final Integer channelPoolHashCode = channel.attr(channelTagPoolKey).get();
                        if (channelPoolHashCode  == null) {
                            channel.attr(channelTagPoolKey).set(channelPool.hashCode());
                        }

                        // 每次调用都会走这一步进行设置为true
                        message.setDelivery(true);
                        // 判断是哪种策略
                        if (RouterStrategyEnum.BACKTRACK.equals(IMContext.SERVER_CONFIG.getClusterServerRouteStrategy())) {
                            // 发送成功
                            // 当targetServerAddress不是toSocketAddress的时候成功了就需要添加进入
                            if (!toSocketAddressStr.equals(message.getTargetServerAddress())) {
                                boolean isExist = false;
                                List<String> currentRoutedServerAddresses = new ArrayList<>();
                                List<String> routedServerAddresses = new ArrayList<>();
                                Iterator<RoutingTable> routingTableIterator = routingTables.iterator();
                                while (routingTableIterator.hasNext()) {
                                    RoutingTable routedTable = routingTableIterator.next();
                                    String serverAddress = routedTable.getServerAddress();
                                    routedServerAddresses.add(serverAddress);
                                    // 判断路由表中没有没有toSocketAddress,如果没有则添加
                                    if (toSocketAddressStr.equals(serverAddress)) {
                                        isExist = true;
                                        break;
                                    }
                                    // 先找到当前服务追加数据
                                    if (IMContext.LOCAL_ADDRESS.equals(serverAddress)) {
                                        currentRoutedServerAddresses = routedTable.getRoutedServerAddresses();
                                    }
                                }
                                if (!isExist) {
                                    currentRoutedServerAddresses.add(toSocketAddressStr);
                                    // 找到下个服务
                                    routingTables.add(new RoutingTable(toSocketAddressStr, IMContext.LOCAL_ADDRESS, routedServerAddresses));
                                }
                            }
                        }
                        // 最后在刷出去
                        // 客户端将数据写出到中介管道中
                        channel.writeAndFlush(packet);
                        // 用完后进行释放掉
                        channelPool.release(channel);
                    } else {
                        // 获取失败
                        Throwable cause = future.cause();
                        log.warn("客户端获取channel异常！原因: {}", cause.getMessage());
                        // 这里使用线程，会发生线程嵌套，不太合理, 在获取消息时可以考虑下使用线程来处理这个
                        if (RouterStrategyEnum.BACKTRACK.equals(IMContext.SERVER_CONFIG.getClusterServerRouteStrategy())) {
                            if (CollectionUtil.isEmpty(routingTables)) {
                                List<String> routedServerAddresses = new ArrayList<>();
                                routedServerAddresses.add(message.getTargetServerAddress());
                                // 找到上个服务
                                routingTables.add(new RoutingTable(IMContext.LOCAL_ADDRESS, null, routedServerAddresses));
                            }else {
                                // 循环装填
                                Iterator<RoutingTable> routingTableIterator = routingTables.iterator();
                                while (routingTableIterator.hasNext()) {
                                    RoutingTable routedTable = routingTableIterator.next();
                                    // 找到当前服务器
                                    if (IMContext.LOCAL_ADDRESS.equals(routedTable.getServerAddress())) {
                                        boolean isExist = false;
                                        List<String> currentRoutedServerAddresses = routedTable.getRoutedServerAddresses();
                                        Iterator<String> currentRoutedServerAddressesIterator = currentRoutedServerAddresses.iterator();
                                        while (currentRoutedServerAddressesIterator.hasNext()) {
                                            if (toSocketAddressStr.equals(currentRoutedServerAddressesIterator.next())) {
                                                isExist = true;
                                                break;
                                            }
                                        }
                                        if (!isExist) {
                                            routedTable.getRoutedServerAddresses().add(toSocketAddressStr);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
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
     * @param packet
     * @return void
     */
    public static List<RoutingTable> wrapperMessage(InetSocketAddress toSocketAddress,Packet packet) {
        final Message message = (Message) packet.getMessage();
        List<RoutingTable> routingTables = message.routingTables();
        if (routingTables == null) {
            routingTables = new ArrayList<>();
        }
        // 定义本地socketAddress,排除自己
        // 本地地址
        String toServerAddress = SocketAddressUtil.convert2HostPort(toSocketAddress);
        // 目标地址,这个方法是通用的
        // 如果目标机地址与下一个路由服务的地址相同则添加本地socketAddress 到消息中，否则添加toSocketAddress
        if (toServerAddress.equals(message.getTargetServerAddress())) {
            routingTables.add(new RoutingTable(IMContext.LOCAL_ADDRESS));
        } else {
            routingTables.add(new RoutingTable(toServerAddress));
        }
        message.setRoutingTables(routingTables);
        // 直接返回
        return routingTables;
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
        deliveryMessage(availableSocketAddress, packet);
    }
}
