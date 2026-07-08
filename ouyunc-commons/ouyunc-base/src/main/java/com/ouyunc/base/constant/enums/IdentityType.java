package com.ouyunc.base.constant.enums;

import java.util.Objects;

/**
 * 会话 / 已读偏移等存储与缓存键上的「会话形态」：仅区分一对一私聊与群聊。
 */
public enum IdentityType {

    ONE_2_ONE(1, "one_2_one", "一对一"),

    GROUP(2, "group", "群聊"),

    /** 客服咨询单 ticket 维度已读 offset（{@code to=ticketId}）。 */
    CUSTOMER_SERVICE(3, "customer_service", "客服咨询单"),

    ;

    private static final String UNREAD_FIELD_SEP = ":";

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

    /**
     * 用户设备未读 Hash 的 field：{@code {sessionType}:{peerOrGroupId}}。
     * 单聊写入 ur Hash；群聊当前不落 Hash，保留编码以便协议统一。
     */
    public String unreadField(String peerOrGroupId) {
        return value + UNREAD_FIELD_SEP + peerOrGroupId;
    }

  /**
     * 从未读 Hash field 解析会话类型。
     */
    public static IdentityType parseUnreadFieldType(String field) {
        if (field == null || field.isEmpty()) {
            return null;
        }
        int sep = field.indexOf(UNREAD_FIELD_SEP);
        if (sep <= 0) {
            return null;
        }
        try {
            return valueOf(Integer.parseInt(field.substring(0, sep)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从未读 Hash field 解析对端用户 id 或群 id。
     */
    public static String parseUnreadFieldPeerOrGroupId(String field) {
        if (field == null || field.isEmpty()) {
            return null;
        }
        int sep = field.indexOf(UNREAD_FIELD_SEP);
        if (sep < 0 || sep >= field.length() - 1) {
            return null;
        }
        return field.substring(sep + 1);
    }

    public static boolean isUnreadFieldOfType(String field, IdentityType expected) {
        return expected != null && expected == parseUnreadFieldType(field);
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
