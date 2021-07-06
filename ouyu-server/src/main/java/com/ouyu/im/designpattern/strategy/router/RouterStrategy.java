package com.ouyu.im.designpattern.strategy.router;

import com.ouyu.im.packet.Packet;

import java.net.InetSocketAddress;

/**
 * @Author fangzhenxun
 * @Description:
 * @Version V1.0
 **/
public interface RouterStrategy {

    /**
     * @Author fangzhenxun
     * @Description 根据路由返回具体的channel连接池
     * @param
     * @return io.netty.channel.pool.ChannelPool
     */
    InetSocketAddress route(InetSocketAddress toSocketAddress, Packet packet);
}
