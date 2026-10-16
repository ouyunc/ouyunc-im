package com.ouyunc.message.safety;

import com.ouyunc.base.constant.enums.TextModerationVendorEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 文本审核厂商策略：未接入的厂商不能放行。
 */
class TextModerationStrategiesTest {

    @Test
    void noneSkipsAndTencentIsTheLiveStrategy() {
        assertNull(TextModerationStrategies.select(null));
        assertNull(TextModerationStrategies.select("  "));
        assertNull(TextModerationStrategies.select("NONE"));
        assertEquals(TextModerationVendorEnum.TENCENT, TextModerationStrategies.select("tencent").vendor());
    }

    @Test
    void pendingVendorsDoNotPass() {
        assertThrows(ContentSafetyUnavailableException.class,
                () -> TextModerationStrategies.select("ALIYUN").moderate("文本", "m1"));
        assertThrows(ContentSafetyUnavailableException.class,
                () -> TextModerationStrategies.select("SHUMEI").moderate("文本", "m1"));
    }

    @Test
    void unknownVendorDoesNotPass() {
        assertThrows(ContentSafetyUnavailableException.class, () -> TextModerationStrategies.select("OTHER"));
    }
}
