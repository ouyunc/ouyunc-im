package com.ouyunc.im.router;

import cn.hutool.json.JSONUtil;
import com.ouyunc.im.base.RoutingTable;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.thread.IMRouteFailureProcessorThread;
import com.ouyunc.im.utils.MapUtil;
import com.ouyunc.im.utils.SocketAddressUtil;
import io.netty.channel.pool.ChannelPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;

/**
 * @Author fangzhenxun
 * @Description: 回溯路由算法
 * @Version V3.0
 **/
public class BacktrackRouterStrategy implements RouterStrategy{
    private static Logger log = LoggerFactory.getLogger(BacktrackRouterStrategy.class);

    /**
     * @Author fangzhenxun
     * @Description 回溯路由,找出一个有效的路由
     * @param toSocketAddress
     * @param packet
     * @return java.net.InetSocketAddress
     */
    @Override
    public InetSocketAddress route(Packet packet, InetSocketAddress toSocketAddress) {
        // 获得消息
        Message message = (Message) packet.getMessage();
        ExtraMessage extraMessage = JSONUtil.toBean(message.getExtra(), ExtraMessage.class);
        if (extraMessage == null) {
            extraMessage = new ExtraMessage();
        }
        // 获取消息中的路由表
        List<RoutingTable> routingTables = extraMessage.routingTables();
        String toSocketAddressStr = SocketAddressUtil.convert2HostPort(toSocketAddress);
        // 判断路由表中是否存在本机服务（是否路由过）
        Iterator<RoutingTable> tableIterator = routingTables.iterator();
        boolean isContain = false;
        // 当前路由表
        RoutingTable localRoutingTable = null;
        // 当前路由上一个路由服务
        while (tableIterator.hasNext()){
            RoutingTable routingTable = tableIterator.next();
            // 如果路由表有本机的记录，则，取出路由过的服务地址，并追加
            if (routingTable.getServerAddress().equals(IMServerContext.SERVER_CONFIG.getLocalServerAddress())) {
                // set 存储会自动去重
                routingTable.getRoutedServerAddresses().add(toSocketAddressStr);
                // 将本地路由表赋值
                localRoutingTable = routingTable;
                isContain = true;
                break;
            }
        }
        // 判断是否是回溯服务的时候失败，如果是回溯的失败则直接走下面的重试机制 localRoutingTable 可能为空
        if (localRoutingTable == null || !toSocketAddressStr.equals(localRoutingTable.getPreServerAddress())) {
            // 如果没有路由过该服务节点，则进行追加本机服务节点路由表
            if (!isContain) {
                Set<String> routedServerAddresses = new HashSet<>();
                routedServerAddresses.add(toSocketAddressStr);
                // 如何找到上一个传递消息的服务地址， extraMessage.getFromServerAddress()可能为空，在第一个节点上
                localRoutingTable = new RoutingTable(IMServerContext.SERVER_CONFIG.getLocalServerAddress(), extraMessage.getFromServerAddress(), routedServerAddresses);
                routingTables.add(localRoutingTable);
            }
            // 设置message
            message.setExtra(JSONUtil.toJsonStr(extraMessage));
            // 下面是挑选一个符合规则的服务
            // 从全量服务注册表中排除一下路由，找出一个符合条件的
            Iterator<Map.Entry<InetSocketAddress, ChannelPool>> allSocketAddressIterator = MapUtil.mergerMaps(IMServerContext.CLUSTER_ACTIVE_SERVER_REGISTRY_TABLE.asMap(), IMServerContext.CLUSTER_GLOBAL_SERVER_REGISTRY_TABLE.asMap()).entrySet().iterator();
            while (allSocketAddressIterator.hasNext()){
                Map.Entry<InetSocketAddress, ChannelPool> next = allSocketAddressIterator.next();
                InetSocketAddress inetSocketAddress = next.getKey();
                String inetSocketAddressStr = SocketAddressUtil.convert2HostPort(inetSocketAddress);
                boolean isExists = false;
                // 排除一下路由信息
                // 在路由表中找出服务节点之前的所有路由(不包括本地路由),localRoutingTable一定不为空
                for (int i = 0; i < routingTables.indexOf(localRoutingTable); i++) {
                    if (routingTables.get(i).getServerAddress().equals(inetSocketAddressStr)) {
                        isExists = true;
                        break;
                    }
                }
                // 如果上面拦截到在这里拦截
                if (!isExists && !IMServerContext.SERVER_CONFIG.getLocalServerAddress().equals(inetSocketAddressStr) && !localRoutingTable.getRoutedServerAddresses().contains(inetSocketAddressStr)) {
                    // 如果都拦截则，找到符合条件的，返回出去
                    return inetSocketAddress;
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
                    }
                }
                return SocketAddressUtil.convert2SocketAddress(localRoutingTable.getPreServerAddress());
            }
        }
        log.warn("获取不到可用的服务连接！开始进行重试...");
        // 将需要处理的重试消息，放到任务队列中, 使用netty中的线程池以及队列，在第一次调用execute时会启动java线程，其实是个死循环来循环处理任务
        EVENT_EXECUTORS.execute(new IMRouteFailureProcessorThread(packet));
        // 抛出一个异常信息
        return null;
    }
}
