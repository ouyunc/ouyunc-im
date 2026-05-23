package com.ouyunc.base.constant.enums;

/**
 * lua 脚本枚举（预加载扩展用）。会话 ZSet 批量校验已改为 Pipeline ZSCORE，兼容旧版 Redis。
 */
public enum LuaScriptEnum {

    READ_OFFSET_MAX_SCRIPT("0", """
            local cur = redis.call('GET', KEYS[1])
            local inc = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])
            local merged = inc
            if cur then
              local c = tonumber(cur)
              if c and c > merged then merged = c end
            end
            redis.call('SET', KEYS[1], tostring(merged), 'PX', ttl)
            return merged
            """, "已读会话偏移量");
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
