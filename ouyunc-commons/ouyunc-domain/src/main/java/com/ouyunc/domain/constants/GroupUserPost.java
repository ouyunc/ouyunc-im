package com.ouyunc.domain.constants;

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
}
