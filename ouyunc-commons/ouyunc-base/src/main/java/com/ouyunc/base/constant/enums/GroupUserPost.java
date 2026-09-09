package com.ouyunc.base.constant.enums;

import java.util.Objects;

/**
 * 群用户职位
 */
public enum GroupUserPost {
    ORDINARY(0, "普通群成员"),
    MANAGER(1, "管理员"),
    LEADER(2, "群主"),
    ;

    private final Integer value;
    private final String desc;

    GroupUserPost(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public Integer value() {
        return value;
    }

    public String desc() {
        return desc;
    }
    public static GroupUserPost valueOf(Integer value) {
        for (GroupUserPost appStatus : values()) {
            if (Objects.equals(appStatus.value, value)) {
                return appStatus;
            }
        }
        return null;
    }

    /**
     * 全员禁言时仍可发言的职位：群主、管理员。
     */
    public static boolean isManagerOrLeader(Integer post) {
        return Objects.equals(post, MANAGER.value) || Objects.equals(post, LEADER.value);
    }
}
