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
     * 单聊收消息：packetId 大于本端 sro 时把 packetId 记入未读集合，计数=SCARD（带上限）。
     * 不用雪花 ID 相减；重复 packetId 靠 SADD 幂等。
     * KEYS[1]=ur KEYS[2]=sro KEYS[3]=uridSet
     * ARGV[1]=field ARGV[2]=packetId ARGV[3]=delta(忽略,兼容) ARGV[4]=storeMax ARGV[5]=ttlMs
     */
    UNREAD_INCR_ONE2ONE_SCRIPT("2", """
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
            local card = toIntOrZero(redis.call('SCARD', KEYS[3]))
            if card >= storeMax then
                redis.call('HSET', KEYS[1], ARGV[1], storeMax)
                return storeMax
            end
            redis.call('SADD', KEYS[3], tostring(pid))
            local nv = toIntOrZero(redis.call('SCARD', KEYS[3]))
            if nv > storeMax then
                nv = storeMax
            end
            redis.call('HSET', KEYS[1], ARGV[1], nv)
            local ttl = toIntOrZero(ARGV[5])
            if ttl > 0 then
                redis.call('PEXPIRE', KEYS[1], ttl)
                redis.call('PEXPIRE', KEYS[3], ttl)
            end
            return nv
            """, "单聊未读增量"),

    /**
     * 单聊本端已读：推进 sro；从未读集合中只移除 {@code packetId <= incomingOffset} 的成员，
     * 再按剩余 SCARD 回写 Hash 计数。无序索引（升级前旧数据）时不整 field HDEL，避免误清更高未读。
     * KEYS[1]=ur KEYS[2]=sro KEYS[3]=uridSet
     * ARGV[1]=field ARGV[2]=incomingOffset ARGV[3]=ttlMs
     */
    UNREAD_CLEAR_ONE2ONE_ON_READ_SCRIPT("3", """
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
                redis.call('PEXPIRE', KEYS[3], ttl)
            else
                redis.call('SET', KEYS[2], merged)
            end
            local ids = redis.call('SMEMBERS', KEYS[3])
            if #ids > 0 then
                for _, id in ipairs(ids) do
                    if packetIdLe(id, inc) then
                        redis.call('SREM', KEYS[3], id)
                    end
                end
                local left = toIntOrZero(redis.call('SCARD', KEYS[3]))
                if left == 0 then
                    redis.call('HDEL', KEYS[1], ARGV[1])
                else
                    redis.call('HSET', KEYS[1], ARGV[1], left)
                end
            end
            return merged
            """, "单聊已读清未读"),

    /**
     * 单聊撤回：从未读 SET 移除指定 packetId，并按剩余 SCARD 回写 Hash 计数。
     * KEYS[1]=ur KEYS[2]=uridSet
     * ARGV[1]=field ARGV[2]=packetId ARGV[3]=ttlMs
     */
    UNREAD_REMOVE_ONE2ONE_ON_WITHDRAW_SCRIPT("2", """
            local function toIntOrZero(v)
                if v == false or v == nil then return 0 end
                if type(v) == 'string' and v == '' then return 0 end
                local n = tonumber(v)
                if n == nil then return 0 end
                return n
            end
            local pid = ARGV[2]
            if pid == nil or pid == '' then
                return toIntOrZero(redis.call('HGET', KEYS[1], ARGV[1]))
            end
            redis.call('SREM', KEYS[2], tostring(pid))
            local left = toIntOrZero(redis.call('SCARD', KEYS[2]))
            if left == 0 then
                redis.call('HDEL', KEYS[1], ARGV[1])
            else
                redis.call('HSET', KEYS[1], ARGV[1], left)
            end
            local ttl = toIntOrZero(ARGV[3])
            if ttl > 0 then
                redis.call('PEXPIRE', KEYS[1], ttl)
                redis.call('PEXPIRE', KEYS[2], ttl)
            end
            return left
            """, "单聊撤回清未读"),

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
     * 单聊/群聊 session 最后消息 lm max-merge（字符串比较，避免雪花 ID 浮点精度问题）。
     * KEYS[1]=lmKey  ARGV[1]=incomingPacketId  ARGV[2]=ttlMs
     */
    SESSION_LM_MAX_SCRIPT("1", """
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
            """, "会话 lm max-merge"),

    /**
     * 最后消息指针 CAS 替换（撤回回退用）：仅当当前值仍等于 expected 时写入 new 或删除。
     * KEYS[1]=lmKey  ARGV[1]=expectedPacketId  ARGV[2]=newPacketId(空=删除)  ARGV[3]=ttlMs
     * 返回 1=已替换，0=指针已变未改写。
     */
    LM_CAS_REPLACE_SCRIPT("1", """
            local function toIntOrZero(v)
                if v == false or v == nil then return 0 end
                if type(v) == 'string' and v == '' then return 0 end
                local n = tonumber(v)
                if n == nil then return 0 end
                return n
            end
            local cur = redis.call('GET', KEYS[1])
            local expect = tostring(ARGV[1])
            if cur == false or cur == nil then cur = '' else cur = tostring(cur) end
            if cur ~= expect then
                return 0
            end
            local neu = ARGV[2]
            if neu == false or neu == nil or neu == '' then
                redis.call('DEL', KEYS[1])
                return 1
            end
            local ttl = toIntOrZero(ARGV[3])
            if ttl > 0 then
                redis.call('SET', KEYS[1], tostring(neu), 'PX', ttl)
            else
                redis.call('SET', KEYS[1], tostring(neu))
            end
            return 1
            """, "lm CAS 替换"),

    /**
     * 群成员 ZSet 回源重建 CAS：仅当 relationVersion 仍等于 expected 时 DEL+ZADD。
     * KEYS[1]=memberZSet KEYS[2]=versionKey
     * ARGV[1]=expectedVersion ARGV[2]=memberCount ARGV[3..]=userId,score 交替
     * 返回 1=已重建，0=版本已变跳过。
     */
    GROUP_MEMBER_REBUILD_CAS_SCRIPT("2", """
            local expected = tostring(ARGV[1])
            local cur = redis.call('GET', KEYS[2])
            if cur == false or cur == nil then cur = '0' else cur = tostring(cur) end
            if cur ~= expected then
                return 0
            end
            redis.call('DEL', KEYS[1])
            local n = tonumber(ARGV[2]) or 0
            for i = 1, n do
                local base = 2 + (i - 1) * 2
                local score = tonumber(ARGV[base + 1]) or 0
                local member = ARGV[base + 2]
                if member ~= nil and member ~= '' then
                    redis.call('ZADD', KEYS[1], score, member)
                end
            end
            return 1
            """, "群成员回源 CAS 重建"),

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
     * 客服 ticket 收消息：packetId 大于本端 ticket sro 时记入未读集合，计数=SCARD。
     * KEYS[1]=urHash KEYS[2]=sroHash KEYS[3]=uridSet
     * ARGV[1]=field ARGV[2]=packetId ARGV[3]=delta(忽略) ARGV[4]=storeMax ARGV[5]=ttlMs
     */
    CS_TICKET_UNREAD_INCR_SCRIPT("2", """
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
            local card = toIntOrZero(redis.call('SCARD', KEYS[3]))
            if card >= storeMax then
                redis.call('HSET', KEYS[1], field, storeMax)
                return storeMax
            end
            redis.call('SADD', KEYS[3], tostring(pid))
            local nv = toIntOrZero(redis.call('SCARD', KEYS[3]))
            if nv > storeMax then
                nv = storeMax
            end
            redis.call('HSET', KEYS[1], field, nv)
            local ttl = toIntOrZero(ARGV[5])
            if ttl > 0 then
                redis.call('PEXPIRE', KEYS[1], ttl)
                redis.call('PEXPIRE', KEYS[2], ttl)
                redis.call('PEXPIRE', KEYS[3], ttl)
            end
            return nv
            """, "客服 ticket 未读增量"),

    /**
     * 客服 ticket 已读：推进 sro Hash；从未读集合只移除 {@code <= incomingOffset}，再回写计数。
     * 无序索引时不整 field HDEL。
     * KEYS[1]=urHash KEYS[2]=sroHash KEYS[3]=uridSet
     * ARGV[1]=field ARGV[2]=incomingOffset ARGV[3]=ttlMs
     */
    CS_TICKET_CLEAR_UNREAD_ON_READ_SCRIPT("2", """
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
                redis.call('PEXPIRE', KEYS[3], ttl)
            end
            local ids = redis.call('SMEMBERS', KEYS[3])
            if #ids > 0 then
                for _, id in ipairs(ids) do
                    if packetIdLe(id, inc) then
                        redis.call('SREM', KEYS[3], id)
                    end
                end
                local left = toIntOrZero(redis.call('SCARD', KEYS[3]))
                if left == 0 then
                    redis.call('HDEL', KEYS[1], field)
                else
                    redis.call('HSET', KEYS[1], field, left)
                end
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
