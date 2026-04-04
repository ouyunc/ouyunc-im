package com.ouyunc.base.constant.enums;

/**
 * HTTP 触发推送时的渠道：决定后续是否走长连接 IM、厂商推送等（当前仅实现 IM）。
 * HUAWEI/APPLE...
 */
public enum PushChannelEnum {

    IM(0, "im", "长连接 IM 下行"),

    ;

    private final int code;
    private final String alias;
    private final String description;

    PushChannelEnum(int code, String alias, String description) {
        this.code = code;
        this.alias = alias;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getAlias() {
        return alias;
    }

    public String getDescription() {
        return description;
    }

    /**
     * @param code 与 JSON 字段 pushChannel 对应，省略时默认 {@link #IM}
     */
    public static PushChannelEnum resolve(Integer code) {
        if (code == null) {
            return IM;
        }
        for (PushChannelEnum e : values()) {
            if (e.code == code) {
                return e;
            }
        }
        return null;
    }
}
