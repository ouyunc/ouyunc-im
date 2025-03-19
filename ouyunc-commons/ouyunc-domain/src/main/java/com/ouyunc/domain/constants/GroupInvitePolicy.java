package com.ouyunc.domain.constants;

import java.util.Objects;

/**
 * 群邀请策略
 */
public enum GroupInvitePolicy {
    REQUIRED_VERIFY(0, "需要校验"),
    AUTO_PASS(1, "自动同意")
    ;



    private final Integer value;
    private final String desc;

    GroupInvitePolicy(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public Integer value() {
        return value;
    }

    public String desc() {
        return desc;
    }
    public static GroupInvitePolicy valueOf(Integer value) {
        for (GroupInvitePolicy friendJoinPolicy : values()) {
            if (Objects.equals(friendJoinPolicy.value, value)) {
                return friendJoinPolicy;
            }
        }
        return null;
    }
}
