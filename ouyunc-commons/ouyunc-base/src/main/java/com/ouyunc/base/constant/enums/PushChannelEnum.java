package com.ouyunc.base.constant.enums;

/**
 * HTTP 触发推送时的渠道：决定后续是否走长连接 IM、厂商推送等（当前仅实现 IM）。
 */
public enum PushChannelEnum {

    IM(0, DirectionEnum.INBOUND, "im", "长连接IM上行"),
    WHATSAPP(1, DirectionEnum.INBOUND, "WhatsApp", "WHATSAPP上行"),
    TELEGRAM(2, DirectionEnum.INBOUND, "Telegram", "Telegram上行"),
    LINE(3, DirectionEnum.INBOUND, "Line", "Line上行"),


    ;

    private final Integer code;
    private final DirectionEnum direction;
    private final String name;
    private final String description;

    PushChannelEnum(Integer code, DirectionEnum direction, String name, String description) {
        this.code = code;
        this.direction = direction;
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

    public DirectionEnum getDirection() {
        return direction;
    }

    /**
     * @param code 与 JSON 字段 pushChannel 对应
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
