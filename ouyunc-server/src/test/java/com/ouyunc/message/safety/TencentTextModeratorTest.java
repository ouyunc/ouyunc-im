package com.ouyunc.message.safety;

import com.ouyunc.base.constant.enums.ContentSafetyHitType;
import com.ouyunc.base.model.ContentSafetyResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 腾讯文本审核的处置映射和不可用判定。不访问外网。
 */
class TencentTextModeratorTest {

    @Test
    void passReturnsNullAndBlockOrReviewReject() {
        assertNull(TencentTextModerator.decide("Pass", "Normal", null));

        ContentSafetyResult block = TencentTextModerator.decide("Block", "Polity", new String[]{"示例"});
        assertFalse(block.isPassed());
        assertEquals(ContentSafetyHitType.POLITICS, block.getHitType());
        assertEquals("tms-block:Polity", block.getReason());
        assertEquals(1, block.getHitWords().size());

        ContentSafetyResult review = TencentTextModerator.decide("Review", "Terror", new String[]{" "});
        assertFalse(review.isPassed());
        assertEquals(ContentSafetyHitType.OTHER, review.getHitType());
        assertEquals("tms-review:Terror", review.getReason());
        assertTrue(review.getHitWords().isEmpty());
    }

    @Test
    void missingOrUnknownSuggestionIsUnavailable() {
        assertThrows(ContentSafetyUnavailableException.class,
                () -> TencentTextModerator.decide(null, "Porn", null));
        assertThrows(ContentSafetyUnavailableException.class,
                () -> TencentTextModerator.decide("Maybe", "Porn", null));
    }

    @Test
    void parameterErrorsAreNotRetriedAndOutagesAre() {
        assertFalse(TencentTextModerator.retryable("InvalidParameter.ErrTextContentLen"));
        assertFalse(TencentTextModerator.retryable("InvalidParameterValue.ErrTextContentType"));
        assertTrue(TencentTextModerator.retryable(null));
        assertTrue(TencentTextModerator.retryable("InternalError.ErrTextTimeOut"));
        assertTrue(TencentTextModerator.retryable("RequestLimitExceeded"));
        assertTrue(TencentTextModerator.retryable("UnauthorizedOperation.Unauthorized"));
        assertTrue(ContentSafetyUnavailableException.causedBy(
                new ContentSafetyUnavailableException("腾讯文本审核不可用")));
    }

    @Test
    void timeoutRoundsUpToOneSecondAndDataIdIsSanitized() {
        assertEquals(1, TencentTextModerator.timeoutSeconds(800));
        assertEquals(2, TencentTextModerator.timeoutSeconds(1001));
        assertEquals("msg01", TencentTextModerator.dataId("msg 01"));
        assertNull(TencentTextModerator.dataId("。。"));
        assertEquals(64, TencentTextModerator.dataId("a".repeat(80)).length());
    }
}
