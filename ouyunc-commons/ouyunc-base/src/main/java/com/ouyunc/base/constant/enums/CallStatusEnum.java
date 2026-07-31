package com.ouyunc.base.constant.enums;

/**
 * 通话结果状态（与前端 {@code CALL_STATUS} 的 code 对齐）。
 */
public enum CallStatusEnum {

    COMPLETED(1, "completed", "已接通"),
    MISSED(2, "missed", "未接听"),
    CANCELLED(3, "cancelled", "已取消"),
    REJECTED(4, "rejected", "已拒绝"),
    BUSY(5, "busy", "对方忙"),
    DECLINED(6, "declined", "已拒绝");

    private final int id;
    private final String code;
    private final String name;

    CallStatusEnum(int id, String code, String name) {
        this.id = id;
        this.code = code;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public static CallStatusEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return COMPLETED;
        }
        String c = code.trim().toLowerCase();
        for (CallStatusEnum e : values()) {
            if (e.code.equals(c)) {
                return e;
            }
        }
        return COMPLETED;
    }

    public static CallStatusEnum fromId(Integer id) {
        if (id == null) {
            return COMPLETED;
        }
        for (CallStatusEnum e : values()) {
            if (e.id == id) {
                return e;
            }
        }
        return COMPLETED;
    }
}
