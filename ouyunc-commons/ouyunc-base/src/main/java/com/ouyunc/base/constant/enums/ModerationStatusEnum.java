package com.ouyunc.base.constant.enums;

/**
 * 媒体内容审核状态（{@code Metadata#getModerationStatus()}）。
 * <p>P0 打 NONE；P1 异步审核流转 PENDING → PASS / REJECT / ERROR。</p>
 */
public enum ModerationStatusEnum {

    /** 尚未进入异步审核（或本消息不需要审核）。 */
    NONE,
    /** 审核中（HOLD 占位或先发后审排队）。 */
    PENDING,
    /** 审核通过。 */
    PASS,
    /** 审核拒绝。 */
    REJECT,
    /** 供应商异常等错误终态。 */
    ERROR;

    /**
     * 解析字符串；空或非法返回 {@code defaultStatus}。
     *
     * @param raw           原文
     * @param defaultStatus 默认值
     * @return 枚举
     */
    public static ModerationStatusEnum from(String raw, ModerationStatusEnum defaultStatus) {
        if (raw == null || raw.isBlank()) {
            return defaultStatus;
        }
        try {
            return ModerationStatusEnum.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return defaultStatus;
        }
    }
}
