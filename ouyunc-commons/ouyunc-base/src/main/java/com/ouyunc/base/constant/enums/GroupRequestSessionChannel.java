package com.ouyunc.base.constant.enums;

import java.util.Objects;

/**
 * 加群的渠道 枚举
 */
public enum GroupRequestSessionChannel {
    OTHER(1, "其他")

    ;

    private final Integer value;
    private final String desc;

    GroupRequestSessionChannel(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public Integer value() {
        return value;
    }

    public String desc() {
        return desc;
    }
    public static GroupRequestSessionChannel valueOf(Integer value) {
        for (GroupRequestSessionChannel appStatus : values()) {
            if (Objects.equals(appStatus.value, value)) {
                return appStatus;
            }
        }
        return null;
    }
}
