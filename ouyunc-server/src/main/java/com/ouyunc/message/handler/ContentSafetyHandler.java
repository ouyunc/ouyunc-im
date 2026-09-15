package com.ouyunc.message.handler;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.model.ContentSafetyResult;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.safety.ContentSafetyFacade;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 内容安全处理器：须挂在登录鉴权之后、业务 PRE 之前。
 * <p>敏感词 REJECT 时不向下传递；MASK/AUDIT/PASS 继续 fire。</p>
 */
public class ContentSafetyHandler extends SimpleChannelInboundHandler<Packet> {

    private static final Logger log = LoggerFactory.getLogger(ContentSafetyHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        ContentSafetyResult result;
        try {
            result = ContentSafetyFacade.check(packet);
        } catch (Exception e) {
            log.error("内容安全检查异常，放行以免误杀 packetId={}", packet == null ? null : packet.getPacketId(), e);
            ctx.fireChannelRead(packet);
            return;
        }
        if (result != null && !result.isPassed()) {
            log.warn("内容安全拒绝 packetId={} reason={} hits={}",
                    packet.getPacketId(), result.getReason(), result.getHitWords());
            MessageServerContext.publishEvent(new MessageEvent(
                    ExceptionEventPayload.of(ExceptionCodeEnum.CONTENT_SENSITIVE_REJECT,
                            ExceptionCodeEnum.CONTENT_SENSITIVE_REJECT.getMessage(), packet),
                    MessageEventTypeEnum.EXCEPTION), true);
            return;
        }
        ctx.fireChannelRead(packet);
    }
}
