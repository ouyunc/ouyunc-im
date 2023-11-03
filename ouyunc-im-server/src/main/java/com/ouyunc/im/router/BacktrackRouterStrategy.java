package com.ouyunc.im.router;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.im.base.RoutingTable;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.InnerExtraData;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.thread.IMRouteFailureProcessorThread;
import com.ouyunc.im.utils.MapUtil;
import io.netty.channel.pool.ChannelPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @Author fangzhenxun
 * @Description: 回溯路由算法
 **/
public class BacktrackRouterStrategy implements RouterStrategy{
    private static Logger log = LoggerFactory.getLogger(BacktrackRouterStrategy.class);

    /**
     * @Author fangzhenxun
     * @Description 回溯路由,找出一个有效的路由
     * @param toServerAddress
     * @param packet
     * @return java.net.InetSocketAddress
     */
    @Override
    public String route(Packet packet, String toServerAddress) {
        log.info("正在使用回溯路由算法查找可用服务... ");
        // 获得消息
        Message message = (Message) packet.getMessage();
        // 这里的 message.getExtra() 不可能为空，所以这里不做判空处理了
        ExtraMessage extraMessage = JSON.parseObject(message.getExtra(), ExtraMessage.class);
        InnerExtraData innerExtraData = extraMessage.getInnerExtraData();
        // 获取消息中的路由表
        List<RoutingTable> routingTables = innerExtraData.getRoutingTables();
        // 判断路由表中是否存在本机服务（是否路由过）
        Iterator<RoutingTable> tableIterator = routingTables.iterator();
        boolean isContain = false;
        // 当前路由表
        RoutingTable localRoutingTable = null;
        // 判断该消息是否路由过本服务
        while (tableIterator.hasNext()){
            RoutingTable routingTable = tableIterator.next();
            // 如果路由表有本机的记录，则，取出路由过的服务地址，并追加
            if (routingTable.getServerAddress().equals(IMServerContext.SERVER_CONFIG.getLocalServerAddress())) {
                // set 存储会自动去重
                routingTable.getRoutedServerAddresses().add(toServerAddress);
                // 将本地路由表赋值
                localRoutingTable = routingTable;
                isContain = true;
                break;
            }
        }
        if (localRoutingTable == null || !toServerAddress.equals(localRoutingTable.getPreServerAddress())) {
            // 如果没有路由过该服务节点，则进行追加本机服务节点路由表
            if (!isContain) {
                Set<String> routedServerAddresses = new HashSet<>();
                routedServerAddresses.add(toServerAddress);
                // 如何找到上一个传递消息的服务地址， extraMessage.getFromServerAddress()可能为空，在第一个节点上
                localRoutingTable = new RoutingTable(IMServerContext.SERVER_CONFIG.getLocalServerAddress(), innerExtraData.getFromServerAddress(), routedServerAddresses);
                routingTables.add(localRoutingTable);
                // 设置路由表
                innerExtraData.setRoutingTables(routingTables);
            }
            // 设置message
            message.setExtra(JSON.toJSONString(extraMessage));
            // 下面是挑选一个符合规则的服务
            // 从全量服务注册表中排除一下路由，找出一个符合条件的，这里可以根据一定的算法来有限选择一个合适的，这里只是排除条件随机选择一个
            Iterator<Map.Entry<String, ChannelPool>> allSocketAddressIterator = MapUtil.mergerMaps(IMServerContext.CLUSTER_ACTIVE_SERVER_REGISTRY_TABLE.asMap(), IMServerContext.CLUSTER_GLOBAL_SERVER_REGISTRY_TABLE.asMap()).entrySet().iterator();
            while (allSocketAddressIterator.hasNext()){
                Map.Entry<String, ChannelPool> next = allSocketAddressIterator.next();
                String nextServerAddress = next.getKey();
                boolean isExists = false;
                // 在路由表中所有路由过的线路(不包括本地路由),localRoutingTable一定不为空
                for (int i = 0; i < routingTables.size(); i++) {
                    if (routingTables.get(i).getServerAddress().equals(nextServerAddress)) {
                        isExists = true;
                        break;
                    }
                }
                // 如果上面拦截到在这里拦截
                if (!isExists && !IMServerContext.SERVER_CONFIG.getLocalServerAddress().equals(nextServerAddress) && !localRoutingTable.getRoutedServerAddresses().contains(nextServerAddress)) {
                    // 如果都拦截则，找到符合条件的，返回出去
                    return nextServerAddress;
                }
            }
            // 如果走到这里说明，没有在该节点都已经路由过了，开始回溯,判断是否可回溯（可能是第一个开始节点）
            if (localRoutingTable.getPreServerAddress() != null) {
                // 转换进行回溯,将要回溯的路由地址中加入当前的地址
                Iterator<RoutingTable> iterator = routingTables.iterator();
                while (iterator.hasNext()) {
                    RoutingTable preRoutingTable = iterator.next();
                    if (localRoutingTable.getPreServerAddress().equals(preRoutingTable.getServerAddress())) {
                        preRoutingTable.getRoutedServerAddresses().add(IMServerContext.SERVER_CONFIG.getLocalServerAddress());
                        break;
                    }
                }
                return localRoutingTable.getPreServerAddress();
            }
        }
        // 将需要处理的重试消息，放到任务队列中, 使用netty中的线程池以及队列，在第一次调用execute时会启动java线程，其实是个死循环来循环处理任务
        EVENT_EXECUTORS.execute(new IMRouteFailureProcessorThread(packet));
        // 抛出一个异常信息
        return null;
    }
}
