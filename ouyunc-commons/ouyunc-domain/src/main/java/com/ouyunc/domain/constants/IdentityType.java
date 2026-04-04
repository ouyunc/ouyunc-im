package com.ouyunc.domain.constants;

import java.util.Objects;

/**
 * 会话 / 已读偏移等存储与缓存键上的「会话形态」：仅区分一对一私聊与群聊。
 */
public enum IdentityType {

    ONE_2_ONE(1, "one_2_one", "一对一"),

    GROUP(2, "group", "群聊"),

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
        if (value == null) {
            return null;
        }
        for (IdentityType e : values()) {
            if (Objects.equals(e.value, value)) {
                return e;
            }
        }
        return null;
    }
}
