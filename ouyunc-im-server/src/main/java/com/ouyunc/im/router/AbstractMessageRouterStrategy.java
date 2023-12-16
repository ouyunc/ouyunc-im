package com.ouyunc.im.router;

import com.alibaba.ttl.threadpool.TtlExecutors;
import com.ouyunc.im.packet.Packet;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import jodd.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.ExecutorService;

/**
 * @Author fangzhenxun
 * @Description: 抽象消息路由策略
 **/
public abstract class AbstractMessageRouterStrategy implements RouterStrategy{
    /**
     * IM 全局事件执行器
     */
    ExecutorService EVENT_EXECUTORS = TtlExecutors.getTtlExecutorService(new DefaultEventExecutorGroup(16, ThreadFactoryBuilder.create().setNameFormat("router-fail-processor-pool-%d").get()));

    /**
     * @param
     * @return io.netty.channel.pool.ChannelPool
     * @Author fangzhenxun
     * @Description 查找并返回可用的serverAddress
     */
    public abstract String route(Packet packet, String toServerAddress);
}
