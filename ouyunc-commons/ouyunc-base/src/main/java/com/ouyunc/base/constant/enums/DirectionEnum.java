package com.ouyunc.base.constant.enums;

/**
 * 消息流向枚举：上行/下行
 * 上行INBOUND：外部用户发过来（WhatsApp/Telegram/Line客户→我方系统）
 * 下行OUTBOUND：我方客服发出去（我方IM→外部客户）
 */
public enum DirectionEnum {

    /**
     * 上行：入站消息，外部发给我们
     */
    INBOUND(0, "上行", "外部发给我们"),

    /**
     * 下行：出站消息，我们发给外部
     */
    OUTBOUND(1, "下行", "我们发给外部");

    /** 数据库存储值 */
    private final Integer code;
    /** 标识名称：上行/下行 */
    private final String name;
    /** 业务展示文案 */
    private final String desc;

    DirectionEnum(Integer code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    /** 根据code匹配枚举 */
    public static DirectionEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (DirectionEnum val : values()) {
            if (val.getCode().equals(code)) {
                return val;
            }
        }
        return null;
    }
}