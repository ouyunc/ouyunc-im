package com.ouyunc.base.constant.enums;

/**
 * HTTP 触发推送时的渠道：决定后续是否走长连接 IM、厂商推送等（当前仅实现 IM）。
 * HUAWEI/APPLE...
 */
public enum PushChannelEnum {

    IM(0, "im", "长连接 IM 下行"),

    ;

    private final Integer code;
    private final String name;
    private final String description;

    PushChannelEnum(Integer code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * @param code 与 JSON 字段 pushChannel 对应，
     */
    public static PushChannelEnum getPushChannelEnum(Integer code) {
        for (PushChannelEnum e : values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
