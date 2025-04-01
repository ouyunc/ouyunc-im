package com.ouyunc.domain.constants;

import java.util.Objects;

/**
 * 群用户职位
 */
public enum GroupUserPost {
    LEADER(1, "群主"),
    MANAGER(2, "管理员"),
    ORDINARY(3, "普通群成员"),
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
