package com.ouyunc.im.helper;

import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.router.BacktrackRouterStrategy;
import com.ouyunc.im.router.RandomRouterStrategy;
import com.ouyunc.im.router.RouterStrategy;

import java.net.InetSocketAddress;

/**
 * @Author fangzhenxun
 * @Description: 集群中的消息路由助手,按照一定策略，随机/最优, 默认是随机
 * @Version V3.0
 **/
public class RouterHelper {

    /**
     * 路由策略
     */
    private static RouterStrategy routerStrategy;

    /**
     * 静态代码块初始化具体策略实现
     */
    static {
        switch (IMServerContext.SERVER_CONFIG.getClusterServerRouteStrategy()) {
            case RANDOM:
                routerStrategy = new RandomRouterStrategy();
                break;
            case BACKTRACK:
                routerStrategy = new BacktrackRouterStrategy();
                break;
        }
    }


    /**
     * @Author fangzhenxun
     * @Description 根据一定策略在 IMContext 中的存活连接中寻找channelPoll
     * @param
     * @return io.netty.channel.pool.ChannelPool
     */
    public static InetSocketAddress route(InetSocketAddress toSocketAddress, Packet packet) {
        return routerStrategy.route(toSocketAddress, packet);
    }

}
