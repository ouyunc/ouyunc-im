package com.ouyunc.base.constant.enums;

import org.apache.commons.lang3.StringUtils;

/**
 * 群聊 @ 目标：{@link #AT_ALL} 表示 @ 全体成员（存入 message.at，由服务端展开投递）。
 */
public enum AtTargetEnum {

    AT_ALL("@all", "@全体成员");

    private final String value;
    private final String description;

    AtTargetEnum(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public boolean is(String candidate) {
        return candidate != null && value.equals(StringUtils.trim(candidate));
    }

    public static boolean isAtAll(String candidate) {
        return AT_ALL.is(candidate);
    }
}
