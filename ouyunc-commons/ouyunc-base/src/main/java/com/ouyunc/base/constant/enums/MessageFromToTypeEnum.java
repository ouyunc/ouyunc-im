package com.ouyunc.base.constant.enums;

/**
 * 消息体中 {@code from}/{@code to} 标识串的业务类型，对应 {@link com.ouyunc.base.packet.message.Message#getFromType()}、{@code getToType()} 等整型取值。
 */
public enum MessageFromToTypeEnum implements Type<Integer> {

    /** -1：系统 */
    SYSTEM(-1, "system", "系统"),

    /** 0：机器人 */
    BOT(0, "bot", "机器人"),

    /** 1：用户 */
    USER(1, "user", "用户"),

    /** 2：群聊 */
    GROUP(2, "group", "群聊"),

    /** 3：设备 */
    DEVICE(3, "device", "设备"),

    /** 4：频道 */
    CHANNEL(4, "channel", "频道"),

    /** 5：客服座席 */
    CS_AGENT(5, "cs_agent", "客服"),

    /** 6：客户（客服场景下的访客/客户） */
    CS_VISITOR(6, "cs_visitor", "客户"),

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

    @Override
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
