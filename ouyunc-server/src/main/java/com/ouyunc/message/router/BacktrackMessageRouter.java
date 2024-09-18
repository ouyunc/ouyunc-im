package com.ouyunc.message.router;

import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.RoutingTable;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.MapUtil;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.thread.MessageClusterRouteFailureThread;
import io.netty.channel.pool.ChannelPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @Author fzx
 * @Description: 回溯路由算法
 **/
public class BacktrackMessageRouter extends AbstractMessageRouter {
    private static final Logger log = LoggerFactory.getLogger(BacktrackMessageRouter.class);

    /**
     * @Author fzx
     * @Description 回溯路由, 找出一个有效的路由, 这里的算法有点绕
     */
    @Override
    public String route(Packet packet, String toServerAddress) {
        log.info("正在使用回溯路由算法查找可用服务... ");
        // 获得消息
        Metadata metadata = packet.getMessage().getMetadata();
        // 获取消息中的路由表
        List<RoutingTable> routingTables = metadata.getRoutingTables();
        // 判断路由表中是否存在本机服务（是否路由过）
        Iterator<RoutingTable> routedTableIterator = routingTables.iterator();
        boolean isContain = false;
        // 当前路由表
        RoutingTable localRoutingTable = null;
        // 查找并判断该消息是否路由过本服务,并将本机服务地址 赋值
        while (routedTableIterator.hasNext()) {
            RoutingTable routingTable = routedTableIterator.next();
            // 如果路由表有本机的记录，则，取出路由过的服务地址，并追加
            if (routingTable.getServerAddress().equals(MessageServerContext.serverProperties().getLocalServerAddress())) {
                // set 存储会自动去重
                routingTable.getRoutedServerAddresses().add(toServerAddress);
                // 将本地路由表赋值
                localRoutingTable = routingTable;
                isContain = true;
                break;
            }
        }
        // 如果该消息没有路由过该服务节点，或者路由了，需要排除掉上一个服务地址，不可选择
        if (localRoutingTable == null || !toServerAddress.equals(localRoutingTable.getPreServerAddress())) {
            // 如果没有路由过该服务节点，则进行追加本机服务节点路由表
            if (!isContain) {
                Set<String> routedServerAddresses = new HashSet<>();
                routedServerAddresses.add(toServerAddress);
                // 如何找到上一个传递消息的服务地址， extraMessage.getFromServerAddress()可能为空，在第一个节点上
                localRoutingTable = new RoutingTable(MessageServerContext.serverProperties().getLocalServerAddress(), metadata.getFromServerAddress(), routedServerAddresses);
                routingTables.add(localRoutingTable);
                // 设置路由表
                metadata.setRoutingTables(routingTables);
            }
            // 下面是挑选一个符合规则的服务
            // 从全量服务注册表中排除一下路由，找出一个符合条件的，这里可以根据一定的算法来有限选择一个合适的，这里只是排除条件随机选择一个
            Iterator<Map.Entry<String, ChannelPool>> allSocketAddressIterator = MapUtil.mergerMaps(MessageServerContext.clusterActiveServerRegistryTableCache.asMap(), MessageServerContext.clusterGlobalServerRegistryTableCache.asMap()).entrySet().iterator();
            while (allSocketAddressIterator.hasNext()) {
                Map.Entry<String, ChannelPool> next = allSocketAddressIterator.next();
                String nextServerAddress = next.getKey();
                boolean isExists = false;
                // 在路由表中所有路由过的线路(不包括本地路由),localRoutingTable一定不为空
                for (RoutingTable routingTable : routingTables) {
                    if (routingTable.getServerAddress().equals(nextServerAddress)) {
                        isExists = true;
                        break;
                    }
                }
                // 如果上面拦截到在这里拦截
                if (!isExists && !MessageServerContext.serverProperties().getLocalServerAddress().equals(nextServerAddress) && !localRoutingTable.getRoutedServerAddresses().contains(nextServerAddress)) {
                    // 如果都拦截则，找到符合条件的，返回出去
                    return nextServerAddress;
                }
            }
            // 如果走到这里说明，没有在该节点都已经路由过了，开始回溯,判断是否可回溯（可能是第一个开始节点）
            if (localRoutingTable.getPreServerAddress() != null) {
                // 转换进行回溯,将要回溯的路由地址中加入当前的地址
                for (RoutingTable preRoutingTable : routingTables) {
                    if (localRoutingTable.getPreServerAddress().equals(preRoutingTable.getServerAddress())) {
                        preRoutingTable.getRoutedServerAddresses().add(MessageServerContext.serverProperties().getLocalServerAddress());
                        break;
                    }
                }
                // 返回上一个路由的服务地址
                return localRoutingTable.getPreServerAddress();
            }
        }
        // 将需要处理的重试消息，放到任务队列中, 使用netty中的线程池以及队列，在第一次调用execute时会启动java线程，其实是个死循环来循环处理任务
        routerExecutor.execute(new MessageClusterRouteFailureThread(packet));
        // 返回空
        return null;
    }
}
