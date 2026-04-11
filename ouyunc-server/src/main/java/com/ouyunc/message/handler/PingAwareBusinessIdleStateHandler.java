package com.ouyunc.message.handler;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

/**
 * 业务读空闲：继承 {@link IdleStateHandler}，在 {@link #channelRead} 中对 PING 就地处理且不调用 {@code super}，
 * 从而不触发读完成路径上的空闲计时刷新（与 Netty 4.2 行为一致）；在 {@link #channelIdle} 中发布
 * {@link MessageEventTypeEnum#CLIENT_BUSINESS_SESSION_IDLE}。
 * <p>
 * 有心跳链时上游 {@link HeartBeatHandler} 已截断 PING，本 handler 的 PING 分支通常不会命中；无全局心跳时依赖本类拦截 PING。
 */
public class PingAwareBusinessIdleStateHandler extends IdleStateHandler {

    public PingAwareBusinessIdleStateHandler(long businessIdleSeconds) {
        super(businessIdleSeconds, 0L, 0L, TimeUnit.SECONDS);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Packet packet && MessageTypeEnum.PING_PONG.getType() == packet.getMessageType()) {
            MessageServerContext.messageProcessorCache.get(MessageTypeEnum.PING_PONG.getType()).process(ctx, packet);
            return;
        }
        super.channelRead(ctx, msg);
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
        if (IdleState.READER_IDLE.equals(evt.state())) {
            LoginClientInfo info = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
            if (info != null) {
                MessageServerContext.publishEvent(new MessageEvent(info, MessageEventTypeEnum.CLIENT_BUSINESS_SESSION_IDLE), true);
            }
            return;
        }
        super.channelIdle(ctx, evt);
    }
}
