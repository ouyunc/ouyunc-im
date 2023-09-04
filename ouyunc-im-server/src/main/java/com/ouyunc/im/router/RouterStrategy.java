package com.ouyunc.im.router;

import com.alibaba.ttl.threadpool.TtlExecutors;
import com.ouyunc.im.packet.Packet;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import jodd.util.concurrent.ThreadFactoryBuilder;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;

/**
 * @Author fangzhenxun
 * @Description: 路由策略接口
 **/
public interface RouterStrategy {
    /**
     * IM 全局事件执行器
     */
    ExecutorService EVENT_EXECUTORS  = TtlExecutors.getTtlExecutorService(new DefaultEventExecutorGroup(16, ThreadFactoryBuilder.create().setNameFormat("router-fail-processor-pool-%d").get()));

    /**
     * @Author fangzhenxun
     * @Description 根据路由返回具体的channel连接池
     * @param
     * @return io.netty.channel.pool.ChannelPool
     */
    InetSocketAddress route(Packet packet, InetSocketAddress toSocketAddress);
}
