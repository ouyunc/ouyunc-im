package com.ouyunc.im.exception.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 全局异常处理器
 */
public class GlobalExceptionHandler extends ChannelDuplexHandler {
    private static Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);


    /**
     * 异常处理逻辑
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("通道 channelId: {}发生了异常: {} ", ctx.channel().id(), cause.getMessage());
        //super.exceptionCaught(ctx, cause);
    }
}
