package com.ouyunc.base.constant.enums;

/**
 * 推送目标类型：广播、单用户、多用户、群组
 */
public enum PushTypeEnum {
    /** 广播 */
    BROADCAST(0),
    /** 单用户，to 必填 */
    USER(1),
    /** 多用户，toList 必填 */
    USERS(2),
    /** 群组，to 为群 id */
    GROUP(3)

    ;

    private final int type;
    PushTypeEnum(int type) {
        this.type = type;
    }
    public int getType() {
        return type;
    }
    public static PushTypeEnum getPushTypeEnum(int type) {
        for (PushTypeEnum pushTypeEnum : PushTypeEnum.values()) {
            if (pushTypeEnum.type == type) {
                return pushTypeEnum;
            }
        }
        return null;
    }
}
