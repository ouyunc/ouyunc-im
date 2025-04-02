package com.ouyunc.domain.constants;

import java.util.Objects;

/**
 * 群会话请求方式 枚举
 */
public enum GroupRequestSessionWay {
    ACTIVE(1, "主动加群"),
    INVITED(2, "被动加群(被邀请)"),
    SCAN(3, "扫码加群"),
    ;

    private final Integer value;
    private final String desc;

    GroupRequestSessionWay(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public Integer value() {
        return value;
    }

    public String desc() {
        return desc;
    }
    public static GroupRequestSessionWay valueOf(Integer value) {
        for (GroupRequestSessionWay appStatus : values()) {
            if (Objects.equals(appStatus.value, value)) {
                return appStatus;
            }
        }
        return null;
    }
}
