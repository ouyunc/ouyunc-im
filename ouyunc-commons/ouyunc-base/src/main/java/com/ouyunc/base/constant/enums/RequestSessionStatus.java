package com.ouyunc.base.constant.enums;

import java.util.Objects;

/**
 * 应用状态 枚举
 */
public enum RequestSessionStatus {
    PENDING(0, "待处理"),
    AGREED(1, "已同意"),
    REFUSED(2, "已拒绝"),
    EXPIRED(3, "已过期"),
    INVALIDATED(4, "已失效"),
    AUTO_AGREED(5, "自动同意"),
    ;

    private final Integer value;
    private final String desc;

    RequestSessionStatus(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public Integer value() {
        return value;
    }

    public String desc() {
        return desc;
    }
    public static RequestSessionStatus valueOf(Integer value) {
        for (RequestSessionStatus appStatus : values()) {
            if (Objects.equals(appStatus.value, value)) {
                return appStatus;
            }
        }
        return null;
    }
}
