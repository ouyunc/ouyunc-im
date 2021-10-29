package com.ouyu.im.designpattern.strategy.router;

import cn.hutool.core.collection.CollectionUtil;
import com.ouyu.im.context.IMServerContext;
import com.ouyu.im.entity.RoutingTable;
import com.ouyu.im.helper.MessageHelper;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.thread.IMClientRouteFailureProcessorThread;
import com.ouyu.im.utils.SocketAddressUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * @Author fangzhenxun
 * @Description: 随机路由策略
 * @Version V1.0
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
    public InetSocketAddress route(InetSocketAddress toSocketAddress,  Packet packet){
        log.info("开始寻找可用的服务连接...");
        // 这里逻辑需要改动, 这里应不应该搞一个全局的不可用服务列表？其实没必要，如果搞得化还是需要解析消息，但可能及时发现不可用的服务了
        // 1,  再次解析与封装msg,在扩展字段中取出并添加这个消息目前路由到的不可用的服务，防止下次路由策略的再次路由到该服务上,并且返回该消息经历过部分服务连接中的不可用服务列表(已经路由过的包括toSocketAddress)
        List<RoutingTable> routedUnavailableSocketAddresses = MessageHelper.wrapperMessage(toSocketAddress, packet);
        // 2，通过得到的列表解析路由策略进行服务路由（寻找一个可用的服务连接）, 从路由表中取出服务地址
        for (InetSocketAddress inetSocketAddress : IMServerContext.CLUSTER_SERVER_REGISTRY_TABLE.asMap().keySet()) {
            if (CollectionUtil.isNotEmpty(routedUnavailableSocketAddresses)) {
                boolean isContain = false;
                for (RoutingTable routedUnavailableSocketAddress : routedUnavailableSocketAddresses) {
                    if (routedUnavailableSocketAddress.getServerAddress().equals(SocketAddressUtil.convert2HostPort(inetSocketAddress))) {
                        isContain = true;
                    }
                }
                if (!isContain) {
                    return inetSocketAddress;
                }
                continue;
            }
        }
        // 如果走到下面就证明就该服务可能掉线，需要进行重试
        log.warn("获取不到可用的服务连接！开始进行重试...");
        // 将需要处理的重试消息，放到任务队列中, 使用netty中的线程池以及队列，在第一次调用execute时会启动java线程，其实是个死循环来循环处理任务
        IMServerContext.EVENT_EXECUTORS.execute(new IMClientRouteFailureProcessorThread(packet));
        // 抛出一个异常信息
        return null;
    }

}
