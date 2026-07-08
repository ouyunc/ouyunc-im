package com.ouyunc.base.constant.enums;

import java.util.Objects;

/**
 * 加群策略
 */
public enum GroupJoinPolicy {
    REQUIRED_VERIFY(0, "需要校验"),
    AUTO_PASS(1, "自动同意")
    ;



    private final Integer value;
    private final String desc;

    GroupJoinPolicy(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public Integer value() {
        return value;
    }

    public String desc() {
        return desc;
    }
    public static GroupJoinPolicy valueOf(Integer value) {
        for (GroupJoinPolicy friendJoinPolicy : values()) {
            if (Objects.equals(friendJoinPolicy.value, value)) {
                return friendJoinPolicy;
            }
        }
        return null;
    }
}
