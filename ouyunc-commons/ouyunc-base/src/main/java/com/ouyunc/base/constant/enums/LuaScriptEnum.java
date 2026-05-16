package com.ouyunc.base.constant.enums;

/**
 * lua 脚本枚举（预加载扩展用）。会话 ZSet 批量校验已改为 Pipeline ZSCORE，兼容旧版 Redis。
 */
public enum LuaScriptEnum {

    RESERVED("0", "", "reserved");

    private final String version;

    private final String script;

    private final String description;


    LuaScriptEnum(String version, String script, String description) {
        this.version = version;
        this.script = script;
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public String getScript() {
        return script;
    }

    public String getDescription() {
        return description;
    }
}
