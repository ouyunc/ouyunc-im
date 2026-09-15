package com.ouyunc.base.constant.enums;

/**
 * 内容安全动作。文本默认 {@link #MASK}，媒体默认 {@link #SEND_THEN_REVIEW}，均可按租户配置覆盖。
 */
public enum ContentSafetyAction {

    PASS,
    MASK,
    REJECT,
    AUDIT_ONLY,
    SEND_THEN_REVIEW,
    HOLD;

    public static ContentSafetyAction from(String raw, ContentSafetyAction defaultAction) {
        if (raw == null || raw.isBlank()) {
            return defaultAction;
        }
        try {
            return ContentSafetyAction.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return defaultAction;
        }
    }
}
