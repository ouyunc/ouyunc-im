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
     * 单聊本端已读：推进 sro；仅当 incoming 严格大于当前 sro 时 HDEL 未读 field。
     * 校验与写入同脚本原子完成，避免 Java 校验与 Lua 执行之间的 TOCTOU 误清。
     * KEYS[1]=ur KEYS[2]=sro
     * ARGV[1]=field ARGV[2]=incomingOffset ARGV[3]=ttlMs
     */
    UNREAD_CLEAR_ONE2ONE_ON_READ_SCRIPT("2", """
            local function toIntOrZero(v)
                if v == false or v == nil then return 0 end
                if type(v) == 'string' and v == '' then return 0 end
                local n = tonumber(v)
                if n == nil then return 0 end
                return n
            end
            local function offsetGt(a, b)
                if a == false or a == nil or a == '' then return false end
                if b == false or b == nil or b == '' then return true end
                a = tostring(a)
                b = tostring(b)
                if #a > #b then return true end
                if #a < #b then return false end
                return a > b
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
            local cur = redis.call('GET', KEYS[2])
            local inc = ARGV[2]
            if cur ~= false and cur ~= nil and cur ~= '' and offsetGt(cur, inc) then
                return tostring(cur)
            end
            local ttl = toIntOrZero(ARGV[3])
            local merged = mergeOffset(cur, inc)
            if ttl > 0 then
                redis.call('SET', KEYS[2], merged, 'PX', ttl)
                redis.call('PEXPIRE', KEYS[1], ttl)
            else
                redis.call('SET', KEYS[2], merged)
            end
            if offsetGt(inc, cur) then
                redis.call('HDEL', KEYS[1], ARGV[1])
            end
            return merged
            """, "单聊已读清未读"),

    /**
     * 客服 ticket 最后消息 lm max-merge（同 READ_OFFSET_MAX，防并发覆盖）。
     * KEYS[1]=lmKey  ARGV[1]=incomingPacketId  ARGV[2]=ttlMs
     */
    CS_TICKET_LM_MAX_SCRIPT("1", """
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
            """, "客服 ticket lm max-merge"),

    /**
     * 客服 ticket Hash 已读 offset max-merge。
     * KEYS[1]=sroHash  ARGV[1]=field  ARGV[2]=incomingOffset  ARGV[3]=ttlMs
     */
    CS_TICKET_READ_OFFSET_HASH_SCRIPT("1", """
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
            local field = ARGV[1]
            local inc = ARGV[2]
            local ttl = toIntOrZero(ARGV[3])
            local cur = redis.call('HGET', KEYS[1], field)
            local merged = mergeOffset(cur, inc)
            redis.call('HSET', KEYS[1], field, merged)
            if ttl > 0 then
                redis.call('PEXPIRE', KEYS[1], ttl)
            end
            return merged
            """, "客服 ticket 已读 offset"),

    /**
     * 客服 ticket 收消息：packetId 大于本端 ticket sro 时未读 +1。
     * KEYS[1]=urHash KEYS[2]=sroHash
     * ARGV[1]=field ARGV[2]=packetId ARGV[3]=delta ARGV[4]=storeMax ARGV[5]=ttlMs
     */
    CS_TICKET_UNREAD_INCR_SCRIPT("1", """
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
            local field = ARGV[1]
            local pid = ARGV[2]
            local sro = redis.call('HGET', KEYS[2], field)
            if pid == nil or pid == '' then
                return toIntOrZero(redis.call('HGET', KEYS[1], field))
            end
            if packetIdLe(pid, sro) then
                return toIntOrZero(redis.call('HGET', KEYS[1], field))
            end
            local storeMax = toIntOrZero(ARGV[4])
            if storeMax <= 0 then storeMax = 1000 end
            local cur = toIntOrZero(redis.call('HGET', KEYS[1], field))
            if cur >= storeMax then
                return cur
            end
            local delta = toIntOrZero(ARGV[3])
            if delta <= 0 then delta = 1 end
            local nv = redis.call('HINCRBY', KEYS[1], field, delta)
            nv = toIntOrZero(nv)
            if nv > storeMax then
                redis.call('HSET', KEYS[1], field, storeMax)
                nv = storeMax
            end
            local ttl = toIntOrZero(ARGV[5])
            if ttl > 0 then
                redis.call('PEXPIRE', KEYS[1], ttl)
                redis.call('PEXPIRE', KEYS[2], ttl)
            end
            return nv
            """, "客服 ticket 未读增量"),

    /**
     * 客服 ticket 已读：推进 sro Hash；incoming 严格大于当前 sro 时 HDEL 未读 field。
     * KEYS[1]=urHash KEYS[2]=sroHash
     * ARGV[1]=field ARGV[2]=incomingOffset ARGV[3]=ttlMs
     */
    CS_TICKET_CLEAR_UNREAD_ON_READ_SCRIPT("1", """
            local function toIntOrZero(v)
                if v == false or v == nil then return 0 end
                if type(v) == 'string' and v == '' then return 0 end
                local n = tonumber(v)
                if n == nil then return 0 end
                return n
            end
            local function offsetGt(a, b)
                if a == false or a == nil or a == '' then return false end
                if b == false or b == nil or b == '' then return true end
                a = tostring(a)
                b = tostring(b)
                if #a > #b then return true end
                if #a < #b then return false end
                return a > b
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
            local field = ARGV[1]
            local inc = ARGV[2]
            local cur = redis.call('HGET', KEYS[2], field)
            if cur ~= false and cur ~= nil and cur ~= '' and offsetGt(cur, inc) then
                return tostring(cur)
            end
            local ttl = toIntOrZero(ARGV[3])
            local merged = mergeOffset(cur, inc)
            redis.call('HSET', KEYS[2], field, merged)
            if ttl > 0 then
                redis.call('PEXPIRE', KEYS[2], ttl)
                redis.call('PEXPIRE', KEYS[1], ttl)
            end
            if offsetGt(inc, cur) then
                redis.call('HDEL', KEYS[1], field)
            end
            return merged
            """, "客服 ticket 已读清未读");

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
