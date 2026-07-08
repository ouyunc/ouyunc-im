package com.ouyunc.base.constant.enums;

import java.util.Objects;

/**
 * 应用状态 枚举
 */
public enum AppStatus {
    NORMAL(1, "正常"),
    ABNORMAL(2, "异常"),
    ;

    private final Integer value;
    private final String desc;

    AppStatus(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public Integer value() {
        return value;
    }

    public String desc() {
        return desc;
    }
    public static AppStatus valueOf(Integer value) {
        for (AppStatus appStatus : values()) {
            if (Objects.equals(appStatus.value, value)) {
                return appStatus;
            }
        }
        return null;
    }
}
