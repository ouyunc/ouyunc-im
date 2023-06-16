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
import com.ouyunc.im.utils.SocketAddressUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author fangzhenxun
 * @Description: 随机路由策略
 * @Version V3.0
 **/
public class RandomRouterStrategy implements RouterStrategy{
    private static Logger log = LoggerFactory.getLogger(RandomRouterStrategy.class);



    /**
     * @Author fangzhenxun
     * @Description 随机返回存活连接中的一个连接，并排除自己
     * @param toSocketAddress
     * @param packet
     * @return io.netty.channel.pool.ChannelPool
     */
    @Override
    public InetSocketAddress route(Packet packet, InetSocketAddress toSocketAddress){
        log.info("当前使用随机路由策略 RandomRouterStrategy 来寻找可用的服务连接...");
        // 这里逻辑需要改动, 这里应不应该搞一个全局的不可用服务列表？其实没必要，如果搞得化还是需要解析消息，但可能及时发现不可用的服务了
        // 1,  再次解析与封装msg,在扩展字段中取出并添加这个消息目前路由到的不可用的服务，防止下次路由策略的再次路由到该服务上,并且返回该消息经历过部分服务连接中的不可用服务列表(已经路由过的包括toSocketAddress)
        // 该集合中可能存在不属于CLUSTER_ACTIVE_SERVER_REGISTRY_TABLE 中的数据
        // routedUnavailableSocketAddresses 一定不为null
        Message message = (Message) packet.getMessage();
        ExtraMessage extraMessage = JSON.parseObject(message.getExtra(), ExtraMessage.class);
        InnerExtraData innerExtraData = extraMessage.getInnerExtraData();
        List<RoutingTable> routingTables = innerExtraData.routingTables();
        String toServerAddress = SocketAddressUtil.convert2HostPort(toSocketAddress);
        // 如果目标机地址与最终目标路由服务的地址相同则添加本地socketAddress 到消息中，否则添加toSocketAddress
        if (toServerAddress.equals(innerExtraData.getTargetServerAddress())) {
            routingTables.add(new RoutingTable(IMServerContext.SERVER_CONFIG.getLocalServerAddress()));
        } else {
            routingTables.add(new RoutingTable(toServerAddress));
        }
        // 已经路由不通的服务列表
        Set<String> routedUnavailableSocketAddresses =  routingTables.stream().map(routingTable -> routingTable.getServerAddress()).collect(Collectors.toSet());
        // 将message 重新设置到packet
        message.setExtra(JSON.toJSONString(extraMessage));
        // 2，通过得到的列表解析路由策略进行服务路由（寻找一个可用的服务连接）, 从路由表中取出服务地址
        for (InetSocketAddress inetSocketAddress : MapUtil.mergerMaps(IMServerContext.CLUSTER_ACTIVE_SERVER_REGISTRY_TABLE.asMap(), IMServerContext.CLUSTER_GLOBAL_SERVER_REGISTRY_TABLE.asMap()).keySet()) {
            // 并排除目标服务器
            String inetSocketAddressStr = SocketAddressUtil.convert2HostPort(inetSocketAddress);
            if (!innerExtraData.getTargetServerAddress().equals(inetSocketAddressStr) && !routedUnavailableSocketAddresses.contains(inetSocketAddressStr)) {
                return inetSocketAddress;
            }
        }
        // 如果走到下面就证明就该服务可能掉线，需要进行重试
        log.warn("消息在集群中路由时，获取不到可用的服务连接！开始进行重试...");
        // 将需要处理的重试消息，放到任务队列中, 使用netty中的线程池以及队列，在第一次调用execute时会启动java线程，其实是个死循环来循环处理任务
        EVENT_EXECUTORS.execute(new IMRouteFailureProcessorThread(packet));
        // 结束消息的传递并返回为null
        return null;
    }

}
