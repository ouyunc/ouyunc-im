package com.ouyunc.im.handler;

import com.alibaba.ttl.threadpool.TtlExecutors;
import com.ouyunc.im.context.IMProcessContext;
import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import jodd.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.ExecutorService;

/**
 * 托管处理器
 */
public class TrusteeshipHandler extends SimpleChannelInboundHandler<Packet> {

    private static final ExecutorService EVENT_EXECUTORS = TtlExecutors.getTtlExecutorService(new DefaultEventExecutorGroup(16, ThreadFactoryBuilder.create().setNameFormat("chat-bot-pool-%d").get()));



    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Packet packet) throws Exception {
        // @todo 判断接受者是否被机器人托管，如果是则交给机器人处理器去处理
        if (false) {
            EVENT_EXECUTORS.execute(() -> IMProcessContext.CHAT_BOT_PROCESSOR.get(0).process(packet));
        }
    }
}
