package com.ouyunc.message.handler;

import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.exception.OutboundPacketVerifyException;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 管道尾部异常：编解码、SSL、半包粘包、IO 损坏。
 * <p>业务 process 失败不得 {@code fireExceptionCaught} 到这里，否则会把在线连接误关。</p>
 * <p>本节点出站构包失败只让该次 write 失败，不得把健康连接关掉。</p>
 */
public class ExceptionHandler extends ChannelDuplexHandler {
    private static final Logger log = LoggerFactory.getLogger(ExceptionHandler.class);


    /**
     * 入站协议损坏才关连接；本地出站校验失败不关。
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (OutboundPacketVerifyException.isLocalVerifyFailure(cause)) {
            log.error("本节点出站构包非法, 本次发送失败, 不关闭连接 channelId={}",
                    ctx.channel() == null ? null : ctx.channel().id(), cause);
            return;
        }
        log.error("通道 channelId: {} 发生了异常", ctx.channel().id(), cause);
        MessageServerContext.publishEvent(new MessageEvent(cause, MessageEventTypeEnum.EXCEPTION), true);
        if (ctx.channel() != null && ctx.channel().isActive()) {
            ctx.close();
        }
    }
}
