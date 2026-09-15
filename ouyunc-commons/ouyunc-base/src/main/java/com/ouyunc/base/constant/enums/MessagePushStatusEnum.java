package com.ouyunc.base.constant.enums;

/**
 * HTTP 推送受理状态。
 * <p>{@link #ACCEPTED}：已写入 PENDING，后台异步确认主记录落库；不等于已投递到端。
 * {@link #DUPLICATE}：同 messageId 已 COMMITTED。
 * {@link #PROCESSING}：同 messageId 仍为 PENDING（在途）。
 * {@link #RETRYABLE_FAILED}：后台失败，可用同一 messageId 重试。</p>
 */
public enum MessagePushStatusEnum {

    /** 已受理：PENDING 已占位，落库确认在后台进行。 */
    ACCEPTED("ACCEPTED", "已受理"),
    /** 重复推送：同 messageId 已 COMMITTED。 */
    DUPLICATE("DUPLICATE", "重复推送（已成功落库）"),
    /** 同 messageId 仍在 PENDING，请稍后查询或重试。 */
    PROCESSING("PROCESSING", "处理中"),
    /** 后台落库/投递失败，允许同 messageId 重试。 */
    RETRYABLE_FAILED("RETRYABLE_FAILED", "后台失败可重试"),
    ;

    private final String code;
    private final String description;

    MessagePushStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
