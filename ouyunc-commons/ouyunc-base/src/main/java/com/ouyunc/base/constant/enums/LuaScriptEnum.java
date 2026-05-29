package com.ouyunc.base.constant.enums;

/**
 * lua 脚本枚举（预加载扩展用）。会话 ZSet 批量校验已改为 Pipeline ZSCORE，兼容旧版 Redis。
 */
public enum LuaScriptEnum {

    READ_OFFSET_MAX_SCRIPT("0", """
            local cur = redis.call('GET', KEYS[1])
            local inc = ARGV[1]
            local ttl = tonumber(ARGV[2])
            local merged = inc
            if cur then
                if #cur > #inc then
                    merged = cur
                elseif #cur == #inc and cur > inc then
                    merged = cur
                end
            end
            redis.call('SET', KEYS[1], merged, 'PX', ttl)
            return merged
            """, "已读会话偏移量"),

    /**
     * 单聊收消息：packetId 大于本端 sro 时未读 +1（带上限）。
     * KEYS[1]=ur KEYS[2]=sro
     * ARGV[1]=field ARGV[2]=packetId ARGV[3]=delta ARGV[4]=storeMax ARGV[5]=ttlMs
     */
    UNREAD_INCR_ONE2ONE_SCRIPT("0", """
            local sro = tonumber(redis.call('GET', KEYS[2]) or '0')
            local pid = tonumber(ARGV[2])
            if pid <= sro then
                return tonumber(redis.call('HGET', KEYS[1], ARGV[1]) or '0')
            end
            local storeMax = tonumber(ARGV[4])
            local cur = tonumber(redis.call('HGET', KEYS[1], ARGV[1]) or '0')
            if cur >= storeMax then
                return cur
            end
            local nv = redis.call('HINCRBY', KEYS[1], ARGV[1], tonumber(ARGV[3]))
            if nv > storeMax then
                redis.call('HSET', KEYS[1], ARGV[1], storeMax)
                nv = storeMax
            end
            redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[5]))
            return nv
            """, "单聊未读增量"),

    /**
     * 单聊本端已读：推进 sro 并 HDEL 未读 field。
     * KEYS[1]=ur KEYS[2]=sro
     * ARGV[1]=field ARGV[2]=incomingOffset ARGV[3]=ttlMs
     */
    UNREAD_CLEAR_ONE2ONE_ON_READ_SCRIPT("0", """
            local inc = ARGV[2]
            local ttl = tonumber(ARGV[3])
            local cur = redis.call('GET', KEYS[2])
            local merged = inc
            if cur then
                if #cur > #inc then
                    merged = cur
                elseif #cur == #inc and cur > inc then
                    merged = cur
                end
            end
            redis.call('SET', KEYS[2], merged, 'PX', ttl)
            redis.call('HDEL', KEYS[1], ARGV[1])
            redis.call('PEXPIRE', KEYS[1], ttl)
            return merged
            """, "单聊已读清未读");
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
