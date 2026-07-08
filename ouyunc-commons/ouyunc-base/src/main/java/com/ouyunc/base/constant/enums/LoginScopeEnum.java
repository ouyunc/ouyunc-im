package com.ouyunc.base.constant.enums;

/**
 * 登录 scope：普通客户端与客服座席/访客（与 {@link MessageFromToTypeEnum}、{@link UserTypeEnum} 无必然对应关系）。
 */
public enum LoginScopeEnum implements Type<Integer> {

    /** 1：普通客户端（与消息 {@link MessageFromToTypeEnum#USER} 取值一致） */
    NORMAL(1, "normal", "普通客户端"),

    /** 5：客服座席 */
    CS_AGENT(5, "cs_agent", "客服座席"),

    /** 6：客服访客/客户 */
    CS_VISITOR(6, "cs_visitor", "客服访客"),
    ;

    private final int type;
    private final String name;
    private final String desc;

    LoginScopeEnum(int type, String name, String desc) {
        this.type = type;
        this.name = name;
        this.desc = desc;
    }

    @Override
    public Integer getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String desc() {
        return desc;
    }

    /**
     * 按 {@link #getType()} 数值解析，未知值归一为 {@link #NORMAL}。
     */
    public static LoginScopeEnum fromType(int type) {
        for (LoginScopeEnum e : values()) {
            if (e.type == type) {
                return e;
            }
        }
        return NORMAL;
    }

    /**
     * 按协议名解析（如 {@link #NORMAL} 对应 {@code "normal"}），未知时返回 {@link #NORMAL}。
     */
    public static LoginScopeEnum fromName(String name) {
        if (name == null || name.isEmpty()) {
            return NORMAL;
        }
        for (LoginScopeEnum e : values()) {
            if (e.name.equalsIgnoreCase(name)) {
                return e;
            }
        }
        return NORMAL;
    }

    /**
     * @param scope 登录 content 中的 scope 取值（与 {@link #getType()} 一致）
     */
    public static boolean isCustomerService(int scope) {
        return scope == CS_AGENT.type || scope == CS_VISITOR.type;
    }

    /**
     * 是否为已定义的 scope；未定义的值应拒绝登录，勿用 {@link #fromType(int)} 静默归一。
     * {@code scope=0} 视为未传，与 {@link #NORMAL} 等价（兼容旧客户端）。
     */
    public static boolean isDefinedType(int type) {
        if (type == 0) {
            return true;
        }
        for (LoginScopeEnum e : values()) {
            if (e.type == type) {
                return true;
            }
        }
        return false;
    }

    /**
     * 登录 content 中的 scope 归一化：未传（0）视为普通客户端。
     */
    public static int normalizeScope(int scope) {
        return scope == 0 ? NORMAL.type : scope;
    }
}
