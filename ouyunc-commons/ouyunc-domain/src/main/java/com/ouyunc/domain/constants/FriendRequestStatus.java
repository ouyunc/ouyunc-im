package com.ouyunc.domain.constants;

import java.util.Objects;

/**
 * 应用状态 枚举
 */
public enum FriendRequestStatus {
    PENDING(0, "待处理"),
    AGREED(1, "已同意"),
    REFUSED(2, "已拒绝"),
    EXPIRED(3, "已过期"),
    ;

    private final Integer value;
    private final String desc;

    FriendRequestStatus(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public Integer value() {
        return value;
    }

    public String desc() {
        return desc;
    }
    public static FriendRequestStatus valueOf(Integer value) {
        for (FriendRequestStatus appStatus : values()) {
            if (Objects.equals(appStatus.value, value)) {
                return appStatus;
            }
        }
        return null;
    }
}
