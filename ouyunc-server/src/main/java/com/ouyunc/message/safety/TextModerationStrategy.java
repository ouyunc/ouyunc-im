package com.ouyunc.message.safety;

import com.ouyunc.base.constant.enums.TextModerationVendorEnum;
import com.ouyunc.base.model.ContentSafetyResult;

/**
 * 文本内容安全厂商策略。新增厂商时实现本接口，并在 {@link TextModerationStrategies} 注册。
 * <p>通过返回 null。拒绝返回结果。超时、限流、未接入抛出 {@link ContentSafetyUnavailableException}。</p>
 */
public interface TextModerationStrategy {

    /**
     * @return 厂商标识
     */
    TextModerationVendorEnum vendor();

    /**
     * 审核原文。
     *
     * @param text   非空白正文
     * @param dataId 业务消息 ID，只用于厂商对账
     * @return 拒绝结果；通过返回 null
     */
    ContentSafetyResult moderate(String text, String dataId);
}
