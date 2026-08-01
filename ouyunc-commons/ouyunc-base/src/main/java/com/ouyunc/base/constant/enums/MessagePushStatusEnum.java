package com.ouyunc.base.constant.enums;

/**
 * HTTP 推送受理状态。
 * <p>{@link #ACCEPTED} 与 {@link #DUPLICATE} 均视为调用成功；
 * {@link #PROCESSING} 表示占位冲突且尚无成功记录，可稍后重试（不当成功）。</p>
 */
public enum MessagePushStatusEnum {

    /** 已受理：业务校验通过，落库/投递在后台进行。 */
    ACCEPTED("ACCEPTED", "已受理"),
    /** 重复推送：同 messageId 此前已成功受理。 */
    DUPLICATE("DUPLICATE", "重复推送（已成功受理）"),
    /** 受理冲突，尚未形成成功记录，请重试。 */
    PROCESSING("PROCESSING", "处理中"),
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
