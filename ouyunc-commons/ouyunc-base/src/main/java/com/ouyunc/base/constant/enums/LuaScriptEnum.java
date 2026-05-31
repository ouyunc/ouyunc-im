package com.ouyunc.base.constant.enums;

/**
 * lua 脚本枚举（预加载扩展用）。会话 ZSet 批量校验已改为 Pipeline ZSCORE，兼容旧版 Redis。
 */
public enum LuaScriptEnum {

    /**
     * 已读会话偏移量 max-merge（纯十进制字符串比较，避免 tonumber 雪花 id 为 nil）。
     * KEYS[1]=offsetKey  ARGV[1]=incomingOffset  ARGV[2]=ttlMs
     */
    READ_OFFSET_MAX_SCRIPT("1", """
            local function toIntOrZero(v)
                if v == false or v == nil then return 0 end
                if type(v) == 'string' and v == '' then return 0 end
                local n = tonumber(v)
                if n == nil then return 0 end
                return n
            end
            local function mergeOffset(cur, inc)
                if inc == nil or inc == '' then inc = '0' end
                inc = tostring(inc)
                if cur == false or cur == nil or cur == '' then return inc end
                cur = tostring(cur)
                if #cur > #inc then return cur end
                if #cur == #inc and cur > inc then return cur end
                return inc
            end
            local inc = ARGV[1]
            local ttl = toIntOrZero(ARGV[2])
            local merged = mergeOffset(redis.call('GET', KEYS[1]), inc)
            if ttl > 0 then
                redis.call('SET', KEYS[1], merged, 'PX', ttl)
            else
                redis.call('SET', KEYS[1], merged)
            end
            return merged
            """, "已读会话偏移量"),

    /**
     * 单聊收消息：packetId 大于本端 sro 时未读 +1（带上限）。
     * packetId / sro 为十进制字符串，按长度+字典序比较。
     * KEYS[1]=ur KEYS[2]=sro
     * ARGV[1]=field ARGV[2]=packetId ARGV[3]=delta ARGV[4]=storeMax ARGV[5]=ttlMs
     */
    UNREAD_INCR_ONE2ONE_SCRIPT("1", """
            local function toIntOrZero(v)
                if v == false or v == nil then return 0 end
                if type(v) == 'string' and v == '' then return 0 end
                local n = tonumber(v)
                if n == nil then return 0 end
                return n
            end
            local function packetIdLe(a, b)
                if a == false or a == nil then a = '0' end
                if b == false or b == nil then b = '0' end
                a = tostring(a)
                b = tostring(b)
                if a == '' then a = '0' end
                if b == '' then b = '0' end
                if #a < #b then return true end
                if #a > #b then return false end
                return a <= b
            end
            local sro = redis.call('GET', KEYS[2])
            local pid = ARGV[2]
            if pid == nil or pid == '' then
                return toIntOrZero(redis.call('HGET', KEYS[1], ARGV[1]))
            end
            if packetIdLe(pid, sro) then
                return toIntOrZero(redis.call('HGET', KEYS[1], ARGV[1]))
            end
            local storeMax = toIntOrZero(ARGV[4])
            if storeMax <= 0 then storeMax = 1000 end
            local cur = toIntOrZero(redis.call('HGET', KEYS[1], ARGV[1]))
            if cur >= storeMax then
                return cur
            end
            local delta = toIntOrZero(ARGV[3])
            if delta <= 0 then delta = 1 end
            local nv = redis.call('HINCRBY', KEYS[1], ARGV[1], delta)
            nv = toIntOrZero(nv)
            if nv > storeMax then
                redis.call('HSET', KEYS[1], ARGV[1], storeMax)
                nv = storeMax
            end
            local ttl = toIntOrZero(ARGV[5])
            if ttl > 0 then
                redis.call('PEXPIRE', KEYS[1], ttl)
            end
            return nv
            """, "单聊未读增量"),

    /**
     * 单聊本端已读：推进 sro 并 HDEL 未读 field。
     * KEYS[1]=ur KEYS[2]=sro
     * ARGV[1]=field ARGV[2]=incomingOffset ARGV[3]=ttlMs
     */
    UNREAD_CLEAR_ONE2ONE_ON_READ_SCRIPT("1", """
            local function toIntOrZero(v)
                if v == false or v == nil then return 0 end
                if type(v) == 'string' and v == '' then return 0 end
                local n = tonumber(v)
                if n == nil then return 0 end
                return n
            end
            local function mergeOffset(cur, inc)
                if inc == nil or inc == '' then inc = '0' end
                inc = tostring(inc)
                if cur == false or cur == nil or cur == '' then return inc end
                cur = tostring(cur)
                if #cur > #inc then return cur end
                if #cur == #inc and cur > inc then return cur end
                return inc
            end
            local ttl = toIntOrZero(ARGV[3])
            local merged = mergeOffset(redis.call('GET', KEYS[2]), ARGV[2])
            if ttl > 0 then
                redis.call('SET', KEYS[2], merged, 'PX', ttl)
                redis.call('PEXPIRE', KEYS[1], ttl)
            else
                redis.call('SET', KEYS[2], merged)
            end
            redis.call('HDEL', KEYS[1], ARGV[1])
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
