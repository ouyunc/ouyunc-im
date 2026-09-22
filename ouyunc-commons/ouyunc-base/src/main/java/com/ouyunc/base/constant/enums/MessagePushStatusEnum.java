package com.ouyunc.base.constant.enums;

/**
 * HTTP 推送受理状态。
 * <p>{@link #ACCEPTED}：本请求已完成 MQ confirm + Redis，且幂等已 COMMITTED；不等于端侧必达。
 * 热写完成但幂等提交结果未知时返回 {@link #PROCESSING}，不得返回 ACCEPTED。
 * {@link #DUPLICATE}：同 messageId 此前已 COMMITTED。
 * {@link #PROCESSING}：同 messageId 仍为 PENDING（在途）。
 * {@link #RETRYABLE_FAILED}：本请求 MQ/热写失败，可用同一 messageId 重试。</p>
 */
public enum MessagePushStatusEnum {

    /** 已成功：MQ + Redis 完成，幂等 COMMITTED。 */
    ACCEPTED("ACCEPTED", "已受理（MQ+Redis 成功）"),
    /** 重复推送：同 messageId 已 COMMITTED。 */
    DUPLICATE("DUPLICATE", "重复推送（已成功落库）"),
    /** 同 messageId 仍在 PENDING，请稍后查询或重试。 */
    PROCESSING("PROCESSING", "处理中"),
    /** MQ/热写失败，允许同 messageId 重试。 */
    RETRYABLE_FAILED("RETRYABLE_FAILED", "失败可重试"),
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
