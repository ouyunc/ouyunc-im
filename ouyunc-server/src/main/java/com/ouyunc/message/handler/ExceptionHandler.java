package com.ouyunc.message.handler;

import com.ouyunc.base.exception.MessageException;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 全局消息异常处理器
 */
public class ExceptionHandler extends ChannelDuplexHandler {
    private static final Logger log = LoggerFactory.getLogger(ExceptionHandler.class);


    /**
     * 异常处理逻辑
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("通道 channelId: {} 发生了异常: {} ", ctx.channel().id(), cause.getMessage());
        // 发送事件，统一交给事件再推送mq, 记录日志，发送邮件或短信进行通知
        MessageServerContext.publishEvent(new ExceptionEvent(new MessageException(cause), null), true);
    }
}
