package com.ouyunc.base.constant.enums;

import org.apache.commons.lang3.StringUtils;

/**
 * 消息投递渠道：标识好友/群成员会话走 IM 长连接还是外部厂商通道。
 * <p>
 * 与 {@link com.ouyunc.domain.entity.FriendEntity#channel}、
 * {@link com.ouyunc.domain.entity.GroupUserEntity#channel} 字段值一致，默认 {@link #IM}。
 * <p>
 * 外部渠道（WhatsApp / Telegram 等）在系统内为独立用户与独立好友/群成员关系；
 * IM 用户支持多设备漫游，不通过本枚举表达设备维度。
 */
public enum MessageDeliveryChannelEnum {

    IM(1, "im", "长连接 IM"),
    WHATSAPP(2, "whatsapp", "WhatsApp"),
    TELEGRAM(3, "telegram", "Telegram"),
    LINE(4, "line", "Line"),
    ;

    private final int code;
    private final String key;
    private final String description;

    MessageDeliveryChannelEnum(int code, String key, String description) {
        this.code = code;
        this.key = key;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }

    public boolean isIm() {
        return this == IM;
    }

    public boolean isExternalMessaging() {
        return this == WHATSAPP || this == TELEGRAM || this == LINE;
    }

    public static MessageDeliveryChannelEnum fromCode(Integer code) {
        if (code == null) {
            return IM;
        }
        for (MessageDeliveryChannelEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return IM;
    }

    public static MessageDeliveryChannelEnum fromKey(String key) {
        if (StringUtils.isBlank(key)) {
            return IM;
        }
        for (MessageDeliveryChannelEnum value : values()) {
            if (value.key.equalsIgnoreCase(key)) {
                return value;
            }
        }
        return IM;
    }
}
