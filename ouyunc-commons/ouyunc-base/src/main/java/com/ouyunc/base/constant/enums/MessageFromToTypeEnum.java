package com.ouyunc.base.constant.enums;

/**
 * 消息体中 {@code from}/{@code to} 标识串的业务类型，对应 {@link com.ouyunc.base.packet.message.Message#getFromType()}、{@code getToType()} 等整型取值。
 */
public enum MessageFromToTypeEnum implements Type<Integer>{

    /** 1：用户 */
    USER(1, "user", "用户"),

    /** 2：群聊 */
    GROUP(2, "group", "群聊"),

    /** 3：系统 */
    SYSTEM(3, "system", "系统"),

    /** 4：机器人 */
    BOT(4, "bot", "机器人"),

    /** 5：设备 */
    DEVICE(5, "device", "设备"),

    /** 6：频道 */
    CHANNEL(6, "channel", "频道"),

    ;

    private final int type;
    private final String name;
    private final String desc;

    MessageFromToTypeEnum(int value, String name, String desc) {
        this.type = value;
        this.name = name;
        this.desc = desc;
    }

    public String getName() {
        return name;
    }

    public Integer getType() {
        return type;
    }

    public String desc() {
        return desc;
    }

    public static MessageFromToTypeEnum valueOf(int value) {
        for (MessageFromToTypeEnum e : values()) {
            if (e.type == value) {
                return e;
            }
        }
        return null;
    }


}
