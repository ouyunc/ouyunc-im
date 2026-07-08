package com.ouyunc.base.constant.enums;

/**
 * HTTP 推送受理状态。
 */
public enum MessagePushStatusEnum {

    ACCEPTED("ACCEPTED", "已受理"),
    DUPLICATE("DUPLICATE", "重复推送（幂等命中）"),
    REJECTED("REJECTED", "拒绝"),
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
