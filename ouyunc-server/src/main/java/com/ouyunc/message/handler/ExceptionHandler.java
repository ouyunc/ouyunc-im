package com.ouyunc.message.handler;

import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 管道尾部异常：编解码、SSL、半包粘包、IO 损坏。
 * <p>业务 process 失败不得 {@code fireExceptionCaught} 到这里，否则会把在线连接误关。</p>
 */
public class ExceptionHandler extends ChannelDuplexHandler {
    private static final Logger log = LoggerFactory.getLogger(ExceptionHandler.class);


    /**
     * 记录并发布异常事件后关闭通道，避免解码/协议异常留下半开僵尸连接。
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("通道 channelId: {} 发生了异常", ctx.channel().id(), cause);
        MessageServerContext.publishEvent(new MessageEvent(cause, MessageEventTypeEnum.EXCEPTION), true);
        if (ctx.channel() != null && ctx.channel().isActive()) {
            ctx.close();
        }
    }
}
