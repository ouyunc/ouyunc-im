package com.ouyunc.im.helper;

import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.router.AbstractMessageRouterStrategy;
import com.ouyunc.im.router.BacktrackMessageRouterStrategy;
import com.ouyunc.im.router.RandomMessageRouterStrategy;
import com.ouyunc.im.router.RouterStrategy;

/**
 * @Author fangzhenxun
 * @Description: 集群中的消息路由助手, 按照一定策略，随机/最优, 默认是随机
 **/
public class RouterHelper {

    /**
     * 路由策略
     */
    private static AbstractMessageRouterStrategy routerStrategy;

    /**
     * 静态代码块初始化具体策略实现
     */
    static {
        switch (IMServerContext.SERVER_CONFIG.getClusterServerRouteStrategy()) {
            case RANDOM:
                routerStrategy = new RandomMessageRouterStrategy();
                break;
            case BACKTRACK:
                routerStrategy = new BacktrackMessageRouterStrategy();
                break;
        }
    }


    /**
     * @param
     * @return io.netty.channel.pool.ChannelPool
     * @Author fangzhenxun
     * @Description 根据一定策略在 IMContext 中的存活连接中寻找channelPoll
     */
    public static String route(Packet packet, String toServerAddress) {
        return routerStrategy.route(packet, toServerAddress);
    }

}
