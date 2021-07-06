package com.ouyu.im.designpattern.strategy.router;

import com.ouyu.im.context.IMContext;
import com.ouyu.im.entity.RoutingTable;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.packet.message.Message;
import com.ouyu.im.thread.IMClientRouteFailureProcessorThread;
import com.ouyu.im.utils.MapUtil;
import com.ouyu.im.utils.SocketAddressUtil;
import io.netty.channel.pool.ChannelPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @Author fangzhenxun
 * @Description: 回溯路由算法
 * @Version V1.0
 **/
public class BacktrackRouterStrategy implements RouterStrategy{
    private static Logger log = LoggerFactory.getLogger(BacktrackRouterStrategy.class);

    /**
     * @Author fangzhenxun
     * @Description 回溯路由
     * @param toSocketAddress
     * @param packet
     * @return java.net.InetSocketAddress
     */
    @Override
    public InetSocketAddress route(InetSocketAddress toSocketAddress, Packet packet) {
        // 获得消息
        Message message = (Message) packet.getMessage();
        List<RoutingTable> routingTables = message.routingTables();
        String preServerAddress = null;
        // 循环服务注册表与全局的合并（服务在线列表）
        Iterator<Map.Entry<InetSocketAddress, ChannelPool>> socketAddressIterator = MapUtil.mergerMaps(IMContext.CLUSTER_SERVER_REGISTRY_TABLE.asMap(), IMContext.CLUSTER_GLOBAL_SERVER_CONNECTS_CACHE.asMap()).entrySet().iterator();
        while (socketAddressIterator.hasNext()) {
            boolean isContain = false;
            Map.Entry<InetSocketAddress, ChannelPool> socketAddressChannelPoolEntry = socketAddressIterator.next();
            InetSocketAddress  inetSocketAddress = socketAddressChannelPoolEntry.getKey();
            String socketAddressStr = SocketAddressUtil.convert2HostPort(inetSocketAddress);
            // 消息路由表
            if (routingTables != null) {
                Iterator<RoutingTable> routingTableIterator = routingTables.iterator();
                while (routingTableIterator.hasNext()) {
                    RoutingTable routingTable = routingTableIterator.next();
                    // 找到路由表中的当前服务,有可能第n次才走到这里（满足条件）
                    if (IMContext.LOCAL_ADDRESS.equals(routingTable.getServerAddress())) {
                        preServerAddress = routingTable.getPreServerAddress();
                    }
                    if (routingTable.getServerAddress().equals(socketAddressStr) || routingTable.getRoutedServerAddresses().contains(socketAddressStr)) {
                        isContain = true;
                        break;
                    }

                }
            }

            // 如果不包含在服务列表中就直接返回
            if (!isContain) {
                // 判断socketAddress是否在路由表中，如果不在则返回，如果在则继续
                return inetSocketAddress;
            }
        }
        // 回溯
        if (preServerAddress != null) {
            return SocketAddressUtil.convert2SocketAddress(preServerAddress);
        }

        log.warn("获取不到可用的服务连接！开始进行重试...");
        // 将需要处理的重试消息，放到任务队列中, 使用netty中的线程池以及队列，在第一次调用execute时会启动java线程，其实是个死循环来循环处理任务
        IMContext.EVENT_EXECUTORS.execute(new IMClientRouteFailureProcessorThread(packet));
        // 抛出一个异常信息
        return null;
    }
}
