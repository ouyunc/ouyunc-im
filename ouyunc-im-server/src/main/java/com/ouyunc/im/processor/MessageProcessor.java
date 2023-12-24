package com.ouyunc.im.processor;

import com.alibaba.ttl.threadpool.TtlExecutors;
import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import jodd.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * @Author fangzhenxun
 * @Description: 消息处理器接口
 **/
public interface MessageProcessor {
    Logger log = LoggerFactory.getLogger(MessageProcessor.class);

    /**
     * 线程池事件执行器
     */
    ExecutorService EVENT_EXECUTORS = TtlExecutors.getTtlExecutorService(new DefaultEventExecutorGroup(16, ThreadFactoryBuilder.create().setNameFormat("message-processor-%d").get()));


    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 前置处理器，做认证授权相关处理，在真正处理消息前处理
     */
    default void preProcess(ChannelHandlerContext ctx, Packet packet) {
    }


    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 做逻辑处理
     */
    void doProcess(ChannelHandlerContext ctx, Packet packet);


    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 做后逻辑处理
     */
    default void postProcess(ChannelHandlerContext ctx, Packet packet) {
    }


}
