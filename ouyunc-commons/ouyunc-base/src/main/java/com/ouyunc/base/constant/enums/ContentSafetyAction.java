package com.ouyunc.base.constant.enums;

/**
 * 内容安全处置动作。
 * <p>文本默认 {@link #MASK}，媒体默认 {@link #SEND_THEN_REVIEW}，均可按租户 {@code ContentSafetyPolicy} 覆盖。</p>
 */
public enum ContentSafetyAction {

    /** 未命中或策略放行，原文继续投递。 */
    PASS,
    /** 命中敏感词后脱敏替换，仍落库并投递给对端。 */
    MASK,
    /** 命中后拒绝发送：不落库、不对端可见；发送方收到 40010。 */
    REJECT,
    /** 命中仅记审计，原文照常投递。 */
    AUDIT_ONLY,
    /** 媒体先发后审（P1）：先投递真实内容，异步审核违规再系统撤回。 */
    SEND_THEN_REVIEW,
    /** 媒体先审后发（P1）：发送方 40013，接收方先见占位消息。 */
    HOLD;

    /**
     * 将配置字符串解析为枚举；空值或非法值回退到 {@code defaultAction}。
     *
     * @param raw           配置原文，忽略大小写
     * @param defaultAction 解析失败时的默认动作
     * @return 解析结果或默认值
     */
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
