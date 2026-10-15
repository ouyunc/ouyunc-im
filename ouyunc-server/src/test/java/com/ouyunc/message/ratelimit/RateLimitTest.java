package com.ouyunc.message.ratelimit;

import com.ouyunc.base.constant.MessageConstant;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 令牌桶、按键准入和单连接发送预算。不依赖 Redis。
 */
class RateLimitTest {

    @Test
    void bucketRefillsAfterBurstIsSpent() {
        long[] now = {0L};
        LocalTokenBucket bucket = new LocalTokenBucket(10D, 2D, () -> now[0]);
        assertTrue(bucket.tryAcquire(1D));
        assertTrue(bucket.tryAcquire(1D));
        assertFalse(bucket.tryAcquire(1D));
        now[0] += 100_000_000L;
        assertTrue(bucket.tryAcquire(1D));
        assertFalse(bucket.tryAcquire(1D));
    }

    @Test
    void refundRestoresATokenThatWasTaken() {
        LocalTokenBucket bucket = new LocalTokenBucket(1D, 1D, () -> 0L);
        assertTrue(bucket.tryAcquire(1D));
        assertFalse(bucket.tryAcquire(1D));
        bucket.refund(1D);
        assertTrue(bucket.tryAcquire(1D));
    }

    @Test
    void blankKeyIsRejectedWithoutSharingABucket() {
        KeyedTokenAdmission admission = new KeyedTokenAdmission(10L, Duration.ofMinutes(1), 1D, 1D);
        assertFalse(admission.allow("  ", 1D));
        assertFalse(admission.allow(null, 1D));
        assertTrue(admission.allow("10.0.0.8", 1D));
        assertFalse(admission.allow("10.0.0.8", 1D));
    }

    @Test
    void connectionAddressDoesNotTruncateDistinctIps() {
        assertNull(ConnectionAdmission.normalize(" "));
        assertNull(ConnectionAdmission.normalize("unknown"));
        assertNull(ConnectionAdmission.normalize("x".repeat(65)));
        assertEquals("2001:db8::1", ConnectionAdmission.normalize(" 2001:db8::1 "));
    }

    @Test
    void oversizedFrameDoesNotConsumeTheMessageToken() {
        EmbeddedChannel channel = new EmbeddedChannel();
        long oversized = (long) RateLimitSettings.channelSendByteBurst() + 1L;
        assertEquals(ChannelSendBudget.Decision.REJECT, ChannelSendBudget.admit(channel, oversized));
        int burst = (int) RateLimitSettings.channelSendMessageBurst();
        for (int i = 0; i < burst; i++) {
            assertEquals(ChannelSendBudget.Decision.ALLOW, ChannelSendBudget.admit(channel, 1L),
                    "oversized reject must refund the message token");
        }
    }

    @Test
    void channelBudgetClosesAfterConsecutiveRejects() {
        EmbeddedChannel channel = new EmbeddedChannel();
        int burst = (int) RateLimitSettings.channelSendMessageBurst();
        for (int i = 0; i < burst; i++) {
            assertEquals(ChannelSendBudget.Decision.ALLOW, ChannelSendBudget.admit(channel, 1L));
        }
        for (int i = 0; i < RateLimitSettings.channelSendRejectCloseThreshold() - 1; i++) {
            assertEquals(ChannelSendBudget.Decision.REJECT, ChannelSendBudget.admit(channel, 1L));
        }
        assertEquals(ChannelSendBudget.Decision.CLOSE, ChannelSendBudget.admit(channel, 1L));
    }

    @Test
    void undersizedBurstsAreRaisedToTheProtocolMinimum() {
        assertEquals(MessageConstant.MAX_FRAME_LENGTH, RateLimitSettings.clampChannelByteBurst(1));
        assertEquals(MessageConstant.HTTP_PUSH_TO_LIST_MAX, RateLimitSettings.clampHttpPushBurst(1));
        assertEquals(120_000, RateLimitSettings.clampLoginKeyTtl(60_000, 1_000));
        assertEquals(60D, RateLimitSettings.channelSendMessageBurst(), 0D);
        assertEquals(1_048_576D, RateLimitSettings.channelSendByteBurst(), 0D);
    }
}
