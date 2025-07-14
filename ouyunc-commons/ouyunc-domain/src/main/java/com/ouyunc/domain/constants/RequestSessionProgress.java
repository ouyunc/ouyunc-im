package com.ouyunc.domain.constants;

import java.util.Objects;

/**
 * 群会话请求进度 枚举
 *
 */
public enum RequestSessionProgress {
    JOINING(-1, "加入中"),
    REFUSING(0, "拒绝中"),
    AGREEING(1, "同意中"),
    ;

    private final Integer value;
    private final String desc;

    RequestSessionProgress(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public Integer value() {
        return value;
    }

    public String desc() {
        return desc;
    }
    public static RequestSessionProgress valueOf(Integer value) {
        for (RequestSessionProgress appStatus : values()) {
            if (Objects.equals(appStatus.value, value)) {
                return appStatus;
            }
        }
        return null;
    }
}
