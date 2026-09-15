package com.ouyunc.base.constant.enums;

/**
 * 媒体审核模式（{@code Metadata#getModerationMode()}）。
 * <p>与租户策略 {@link ContentSafetyAction#SEND_THEN_REVIEW}/{@link ContentSafetyAction#HOLD} 对齐，
 * 单独枚举避免与文本 MASK/REJECT 等动作混用。</p>
 */
public enum ModerationModeEnum {

    /** 先发后审：先投递真实媒体，违规再系统撤回。 */
    SEND_THEN_REVIEW,
    /** 先审后发：发送方审核中，接收方先见占位。 */
    HOLD;

    /**
     * 从内容安全动作映射；非媒体动作时回退默认。
     *
     * @param action        策略媒体动作
     * @param defaultMode   默认模式
     * @return 审核模式
     */
    public static ModerationModeEnum fromAction(ContentSafetyAction action, ModerationModeEnum defaultMode) {
        if (action == ContentSafetyAction.HOLD) {
            return HOLD;
        }
        if (action == ContentSafetyAction.SEND_THEN_REVIEW) {
            return SEND_THEN_REVIEW;
        }
        return defaultMode == null ? SEND_THEN_REVIEW : defaultMode;
    }

    /**
     * 解析字符串；空或非法返回 {@code defaultMode}。
     *
     * @param raw         原文
     * @param defaultMode 默认值
     * @return 枚举
     */
    public static ModerationModeEnum from(String raw, ModerationModeEnum defaultMode) {
        if (raw == null || raw.isBlank()) {
            return defaultMode;
        }
        try {
            return ModerationModeEnum.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return defaultMode;
        }
    }
}
