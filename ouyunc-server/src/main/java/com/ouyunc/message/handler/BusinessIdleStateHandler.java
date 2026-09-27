package com.ouyunc.message.handler;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ClientBusinessSessionIdlePayload;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.processor.AbstractMessageBiProcessor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

/**
 * 业务读空闲：继承 {@link IdleStateHandler}，在 {@link #channelRead} 中对 PING 就地处理且不调用 {@code super}；
 * 在 {@link #channelIdle} 中发布 {@link MessageEventTypeEnum#CLIENT_BUSINESS_SESSION_IDLE}，source 为
 * {@link ClientBusinessSessionIdlePayload}（含连续次数 {@code strike} 与 {@code ctx}）。
 * 只要持续无有效业务读，会按 {@code businessIdleSeconds} 间隔反复触发，事件可一直发、{@code strike} 递增。
 * 是否关连由登录 {@link com.ouyunc.base.packet.message.content.LoginContent#getBusinessIdleCloseStrike()} 决定（{@code <=0} 不关，{@code >=1} 为第 N 次关）。
 * 达到次数时先下行提示，写出后再关；本 handler 仅做超时兜底关连。
 * 非 PING 业务上行会清零 {@code strike} 并重新计时。
 */
public class BusinessIdleStateHandler extends IdleStateHandler {

    public BusinessIdleStateHandler(long businessIdleSeconds) {
        super(businessIdleSeconds, 0L, 0L, TimeUnit.SECONDS);
    }

    /**
     * @return -1 表示不因次数关连（{@code businessIdleCloseStrike <= 0}）；否则为第几次关连
     */
    private static int resolveCloseAtStrike(LoginClientInfo loginInfo) {
        int t = loginInfo.getBusinessIdleCloseStrike();
        if (t <= 0) {
            return -1;
        }
        return t;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Packet packet
                && packet.getMessage() != null
                && MessageTypeEnum.PING_PONG.getType() == packet.getMessageType()) {
            AbstractMessageBiProcessor<? extends Number> processor =
                    MessageServerContext.messageProcessorCache.get(MessageTypeEnum.PING_PONG.getType());
            if (processor != null) {
                processor.process(ctx, packet).subscribe(
                        unused -> { },
                        e -> { /* 业务空闲路径心跳失败仅吞掉，避免打断读循环 */ });
            }
            return;
        }
        // 业务上行：清零次数。已预约的 8s 兜底 / 通知后关连见 {@link #isCloseStillDue}，不再关。
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_BUSINESS_IDLE_STRIKE, 0);
        super.channelRead(ctx, msg);
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
        if (!IdleState.READER_IDLE.equals(evt.state())) {
            super.channelIdle(ctx, evt);
            return;
        }
        if (!ctx.channel().isActive()) {
            return;
        }
        Integer prev = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_BUSINESS_IDLE_STRIKE);
        int strike = (prev == null ? 0 : prev) + 1;
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_BUSINESS_IDLE_STRIKE, strike);

        LoginClientInfo info = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        if (info == null) {
            return;
        }
        MessageServerContext.publishEvent(
                new MessageEvent(new ClientBusinessSessionIdlePayload(info, strike, ctx), MessageEventTypeEnum.CLIENT_BUSINESS_SESSION_IDLE),
                true);
        int closeAt = resolveCloseAtStrike(info);
        // 关连改由通知写出回调执行；此处仅兜底，避免 Disruptor 丢事件后连接永不关
        if (closeAt > 0 && strike >= closeAt && ctx.channel().isActive()) {
            ctx.executor().schedule(() -> {
                if (isCloseStillDue(ctx)) {
                    ctx.close();
                }
            }, MessageConstant.BUSINESS_IDLE_CLOSE_AFTER_NOTIFY_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * 业务上行后 strike 被置 0（或尚未计数）。此时空闲提示和关连都应取消。
     */
    public static boolean isIdleStrikeCleared(ChannelHandlerContext ctx) {
        if (ctx == null) {
            return true;
        }
        Integer current = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_BUSINESS_IDLE_STRIKE);
        return current == null || current == 0;
    }

    /**
     * 关连是否仍然成立：连接还在，且当前 strike 仍达到关连档。
     * 8s 内对方发了业务包会把 strike 置 0，此时必须取消关连（含通知写出回调）。
     */
    public static boolean isCloseStillDue(ChannelHandlerContext ctx) {
        if (ctx == null || ctx.channel() == null || !ctx.channel().isActive()) {
            return false;
        }
        LoginClientInfo info = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        if (info == null) {
            return false;
        }
        int closeAt = resolveCloseAtStrike(info);
        if (closeAt <= 0) {
            return false;
        }
        Integer current = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_BUSINESS_IDLE_STRIKE);
        return current != null && current >= closeAt;
    }
}
