package com.ouyunc.im.router;

import com.ouyunc.im.packet.Packet;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;

import java.net.InetSocketAddress;

/**
 * @Author fangzhenxun
 * @Description: 路由策略接口
 **/
public interface RouterStrategy {
    /**
     * IM 全局事件执行器
     */
    EventExecutorGroup EVENT_EXECUTORS= new DefaultEventExecutorGroup(16);

    /**
     * @Author fangzhenxun
     * @Description 根据路由返回具体的channel连接池
     * @param
     * @return io.netty.channel.pool.ChannelPool
     */
    InetSocketAddress route(Packet packet, InetSocketAddress toSocketAddress);
}
