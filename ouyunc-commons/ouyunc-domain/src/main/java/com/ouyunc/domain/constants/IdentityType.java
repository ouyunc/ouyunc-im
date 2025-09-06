package com.ouyunc.domain.constants;

import java.util.Objects;

/**
 * 唯一标识类型 枚举
 */
public enum IdentityType {
    ONE_2_ONE(1, "one_2_one" , "一对一"),

    GROUP(2,"group" , "群组"),
    ;

    private final Integer value;
    private final String name;
    private final String desc;

    IdentityType(Integer value, String name, String desc) {
        this.value = value;
        this.name = name;
        this.desc = desc;
    }

    public String getName() {
        return name;
    }

    public Integer value() {
        return value;
    }

    public String desc() {
        return desc;
    }
    public static IdentityType valueOf(Integer value) {
        for (IdentityType appStatus : values()) {
            if (Objects.equals(appStatus.value, value)) {
                return appStatus;
            }
        }
        return null;
    }
}
