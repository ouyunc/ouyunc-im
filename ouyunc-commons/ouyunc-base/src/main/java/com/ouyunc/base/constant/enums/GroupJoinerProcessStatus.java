package com.ouyunc.base.constant.enums;

import java.util.Objects;

/**
 * 群 加入者处理状态（作用在群成员邀请用户的场景）：加入方处理状态：0-待处理，1-同意邀请，2-拒绝邀请
 */
public enum GroupJoinerProcessStatus {
    PENDING(0, "待处理"),
    AGREE(1, "同意邀请"),
    REFUSE(2, "拒绝邀请"),
    ;

    private final Integer value;
    private final String desc;

    GroupJoinerProcessStatus(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public Integer value() {
        return value;
    }

    public String desc() {
        return desc;
    }
    public static GroupJoinerProcessStatus valueOf(Integer value) {
        for (GroupJoinerProcessStatus status : values()) {
            if (Objects.equals(status.value, value)) {
                return status;
            }
        }
        return null;
    }
}
