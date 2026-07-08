package com.ouyunc.base.constant.enums;

import java.util.Objects;

/**
 * 真假/yes or no 枚举
 */
public enum YesOrNo {
    NO(0,  false, "否"),
    YES(1,  true, "是"),
    ;

    private final Integer code;
    private final Boolean value;
    private final String name;

    YesOrNo(Integer code, Boolean value, String name) {
        this.code = code;
        this.value = value;
        this.name = name;
    }

    public Integer getCode() {
        return code;
    }

    public Boolean getValue() {
        return value;
    }

    public String getName() {
        return name;
    }

    public static YesOrNo codeOf(Integer code) {
        for (YesOrNo yesOrNo : values()) {
            if (Objects.equals(yesOrNo.code, code)) {
                return yesOrNo;
            }
        }
        return null;
    }

    public static YesOrNo valueOf(Boolean value) {
        if (value) {
            return YesOrNo.YES;
        }
        return YesOrNo.NO;
    }
}
