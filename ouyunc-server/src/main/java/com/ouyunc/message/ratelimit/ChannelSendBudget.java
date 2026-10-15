package com.ouyunc.message.ratelimit;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

/**
 * 单连接发送预算。条数和字节同时够用才放行；字节不够时退回已经扣掉的条数。
 * 连续超限达到阈值后关闭连接。心跳和 QoS 确认由调用方跳过，不进入这里。
 */
public final class ChannelSendBudget {

    private static final AttributeKey<ChannelSendBudget> KEY =
            AttributeKey.newInstance("ouyunc.channel.send.budget");

    private final LocalTokenBucket messages;
    private final LocalTokenBucket bytes;
    private final int closeThreshold;
    private int consecutiveRejects;

    /** 允许继续处理、本次拒绝但仍保持连接、或连续超限后应关闭。 */
    public enum Decision {
        ALLOW,
        REJECT,
        CLOSE
    }

    private ChannelSendBudget() {
        this.messages = new LocalTokenBucket(
                RateLimitSettings.channelSendMessagesPerSecond(),
                RateLimitSettings.channelSendMessageBurst());
        this.bytes = new LocalTokenBucket(
                RateLimitSettings.channelSendBytesPerSecond(),
                RateLimitSettings.channelSendByteBurst());
        this.closeThreshold = RateLimitSettings.channelSendRejectCloseThreshold();
    }

    /**
     * 只在该连接的 EventLoop 上调用。预算对象挂在 Channel 上，连接关闭后一起释放。
     * 预算在连接第一次发业务包时按当时配置创建，之后不再跟随配置变化。
     */
    public static Decision admit(Channel channel, long byteCost) {
        if (!RateLimitSettings.channelSendEnabled()) {
            return Decision.ALLOW;
        }
        ChannelSendBudget budget = channel.attr(KEY).get();
        if (budget == null) {
            ChannelSendBudget created = new ChannelSendBudget();
            ChannelSendBudget existing = channel.attr(KEY).setIfAbsent(created);
            budget = existing == null ? created : existing;
        }
        return budget.decide(byteCost);
    }

    private Decision decide(long byteCost) {
        long cost = Math.max(0L, byteCost);
        if (!messages.tryAcquire(1D)) {
            return strike();
        }
        if (cost > 0L && !bytes.tryAcquire(cost)) {
            messages.refund(1D);
            return strike();
        }
        consecutiveRejects = 0;
        return Decision.ALLOW;
    }

    private Decision strike() {
        consecutiveRejects++;
        if (consecutiveRejects >= closeThreshold) {
            return Decision.CLOSE;
        }
        return Decision.REJECT;
    }
}
