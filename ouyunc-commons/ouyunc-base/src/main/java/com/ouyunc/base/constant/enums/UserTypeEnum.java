package com.ouyunc.base.constant.enums;

/**
 * 用户表 {@code ouyunc_im_user.type} 取值（与消息 {@code fromType}、登录 {@code scope} 无必然对应关系）。
 */
public enum UserTypeEnum implements Type<Integer> {

    /** -1：系统账号 */
    SYSTEM(-1, "system", "系统"),

    /** 0：机器人 */
    BOT(0, "bot", "机器人"),

    /** 1：真实用户 */
    USER(1, "user", "真实用户"),

    ;

    private final int type;
    private final String name;
    private final String desc;

    UserTypeEnum(int type, String name, String desc) {
        this.type = type;
        this.name = name;
        this.desc = desc;
    }

    @Override
    public Integer getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String desc() {
        return desc;
    }
}
