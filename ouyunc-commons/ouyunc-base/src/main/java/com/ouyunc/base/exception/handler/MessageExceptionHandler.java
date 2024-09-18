package com.ouyunc.base.exception.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 全局消息异常处理器
 */
public class MessageExceptionHandler extends ChannelDuplexHandler {
    private static final Logger log = LoggerFactory.getLogger(MessageExceptionHandler.class);


    /**
     * 异常处理逻辑
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("通道 channelId: {} 发生了异常: {} ", ctx.channel().id(), cause.getMessage());
        // do nothing
    }
}
