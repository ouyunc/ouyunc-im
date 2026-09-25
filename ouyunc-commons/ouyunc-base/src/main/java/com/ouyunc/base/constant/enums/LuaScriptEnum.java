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
     * 单聊收消息：packetId 大于本端 sro 时记入未读 ZSET（member=19 位 packetId，score=0）。
     * 超过 storeMax 裁掉最旧，保留最新；展示封顶由调用方处理，这里不丢新消息。
     * KEYS[1]=ur KEYS[2]=sro KEYS[3]=uridZset
     * ARGV[1]=field ARGV[2]=packetId ARGV[3]=delta(忽略) ARGV[4]=storeMax ARGV[5]=ttlMs
     */
    UNREAD_INCR_ONE2ONE_SCRIPT("2", """
            local function toIntOrZero(v)
                if v == false or v == nil then return 0 end
                if type(v) == 'string' and v == '' then return 0 end
                local n = tonumber(v)
                if n == nil then return 0 end
                return n
            end
            local function normId(v)
                if v == false or v == nil then return '0' end
                v = tostring(v)
                v = string.gsub(v, '^0+', '')
                if v == '' then return '0' end
                return v
            end
            local function packetIdLe(a, b)
                a = normId(a)
                b = normId(b)
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
            redis.call('ZADD', KEYS[3], 0, tostring(pid))
            local nv = toIntOrZero(redis.call('ZCARD', KEYS[3]))
            if nv > storeMax then
                redis.call('ZREMRANGEBYRANK', KEYS[3], 0, nv - storeMax - 1)
                nv = toIntOrZero(redis.call('ZCARD', KEYS[3]))
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
     * 再按剩余 ZCARD 回写 Hash 计数。
     * KEYS[1]=ur KEYS[2]=sro KEYS[3]=uridZset
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
            local function normId(v)
                if v == false or v == nil then return '0' end
                v = tostring(v)
                v = string.gsub(v, '^0+', '')
                if v == '' then return '0' end
                return v
            end
            local function packetIdLe(a, b)
                a = normId(a)
                b = normId(b)
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
            local ids = redis.call('ZRANGE', KEYS[3], 0, -1)
            if #ids > 0 then
                for _, id in ipairs(ids) do
                    if packetIdLe(id, inc) then
                        redis.call('ZREM', KEYS[3], id)
                    end
                end
                local left = toIntOrZero(redis.call('ZCARD', KEYS[3]))
                if left == 0 then
                    redis.call('HDEL', KEYS[1], ARGV[1])
                else
                    redis.call('HSET', KEYS[1], ARGV[1], left)
                end
            end
            return merged
            """, "单聊已读清未读"),

    /**
     * 单聊撤回：从未读 ZSET 移除指定 packetId，并按剩余 ZCARD 回写 Hash 计数。
     * KEYS[1]=ur KEYS[2]=uridZset
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
            redis.call('ZREM', KEYS[2], tostring(pid))
            local left = toIntOrZero(redis.call('ZCARD', KEYS[2]))
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
     * 群成员回源 CAS：先写同槽临时 ZSET，版本仍匹配再 RENAME，失败只删临时键。
     * KEYS[1]=memberZSet KEYS[2]=versionKey KEYS[3]=initKey KEYS[4]=tmpZset
     * ARGV[1]=expectedVersion ARGV[2]=memberCount ARGV[3..]=score,member 交替
     * 返回 1=已重建并写 INIT，0=版本已变或已有一致 INIT。
     */
    GROUP_MEMBER_REBUILD_CAS_SCRIPT("4", """
            local expected = tostring(ARGV[1])
            local cur = redis.call('GET', KEYS[2])
            if cur == false or cur == nil then cur = '0' else cur = tostring(cur) end
            if cur ~= expected then
                return 0
            end
            local init = redis.call('GET', KEYS[3])
            if init ~= false and init ~= nil then
                local n0 = tonumber(init)
                if n0 ~= nil and math.abs(n0) == redis.call('ZCARD', KEYS[1]) then
                    return 0
                end
            end
            redis.call('DEL', KEYS[4])
            local n = tonumber(ARGV[2]) or 0
            for i = 1, n do
                local base = 2 + (i - 1) * 2
                local score = tonumber(ARGV[base + 1]) or 0
                local member = ARGV[base + 2]
                if member ~= nil and member ~= '' then
                    redis.call('ZADD', KEYS[4], score, member)
                end
            end
            cur = redis.call('GET', KEYS[2])
            if cur == false or cur == nil then cur = '0' else cur = tostring(cur) end
            if cur ~= expected then
                redis.call('DEL', KEYS[4])
                return 0
            end
            redis.call('DEL', KEYS[1])
            if redis.call('EXISTS', KEYS[4]) == 1 then
                redis.call('RENAME', KEYS[4], KEYS[1])
            end
            redis.call('SET', KEYS[3], tostring(n))
            return 1
            """, "群成员回源 CAS 重建"),

    /**
     * 用户加群 ZSET 回源 CAS：先写同槽临时 ZSET，版本仍匹配再 RENAME，失败只删临时键。
     * KEYS[1]=zset KEYS[2]=versionKey KEYS[3]=initKey KEYS[4]=tmpZset
     * ARGV[1]=expectedVersion ARGV[2]=count ARGV[3..]=score,member 交替
     * 返回 1=已重建并写 INIT，0=版本已变或已有一致 INIT。
     */
    USER_GROUPS_REBUILD_SCRIPT("4", """
            local expected = tostring(ARGV[1])
            local cur = redis.call('GET', KEYS[2])
            if cur == false or cur == nil then cur = '0' else cur = tostring(cur) end
            if cur ~= expected then
                return 0
            end
            local init = redis.call('GET', KEYS[3])
            if init ~= false and init ~= nil then
                local n0 = tonumber(init)
                if n0 ~= nil and math.abs(n0) == redis.call('ZCARD', KEYS[1]) then
                    return 0
                end
            end
            redis.call('DEL', KEYS[4])
            local n = tonumber(ARGV[2]) or 0
            for i = 1, n do
                local base = 2 + (i - 1) * 2
                local score = tonumber(ARGV[base + 1]) or 0
                local member = ARGV[base + 2]
                if member ~= nil and member ~= '' then
                    redis.call('ZADD', KEYS[4], score, member)
                end
            end
            cur = redis.call('GET', KEYS[2])
            if cur == false or cur == nil then cur = '0' else cur = tostring(cur) end
            if cur ~= expected then
                redis.call('DEL', KEYS[4])
                return 0
            end
            redis.call('DEL', KEYS[1])
            if redis.call('EXISTS', KEYS[4]) == 1 then
                redis.call('RENAME', KEYS[4], KEYS[1])
            end
            redis.call('SET', KEYS[3], tostring(n))
            return 1
            """, "用户加群名单回源 CAS 重建"),

    /**
     * 好友名单回源 CAS：先写同槽临时 ZSET，版本仍匹配再 RENAME。
     * complete=1 写 INIT=count；complete=0 写 INIT=-count（截断，禁止负向判定）。
     * KEYS[1]=zset KEYS[2]=versionKey KEYS[3]=initKey KEYS[4]=tmpZset
     * ARGV[1]=expectedVersion ARGV[2]=count ARGV[3]=complete ARGV[4..]=score,member 交替
     */
    FRIEND_ROSTER_REBUILD_CAS_SCRIPT("4", """
            local expected = tostring(ARGV[1])
            local cur = redis.call('GET', KEYS[2])
            if cur == false or cur == nil then cur = '0' else cur = tostring(cur) end
            if cur ~= expected then
                return 0
            end
            local init = redis.call('GET', KEYS[3])
            if init ~= false and init ~= nil then
                local n0 = tonumber(init)
                if n0 ~= nil and math.abs(n0) == redis.call('ZCARD', KEYS[1]) then
                    return 0
                end
            end
            redis.call('DEL', KEYS[4])
            local n = tonumber(ARGV[2]) or 0
            for i = 1, n do
                local base = 3 + (i - 1) * 2
                local score = tonumber(ARGV[base + 1]) or 0
                local member = ARGV[base + 2]
                if member ~= nil and member ~= '' then
                    redis.call('ZADD', KEYS[4], score, member)
                end
            end
            cur = redis.call('GET', KEYS[2])
            if cur == false or cur == nil then cur = '0' else cur = tostring(cur) end
            if cur ~= expected then
                redis.call('DEL', KEYS[4])
                return 0
            end
            redis.call('DEL', KEYS[1])
            if redis.call('EXISTS', KEYS[4]) == 1 then
                redis.call('RENAME', KEYS[4], KEYS[1])
            end
            local complete = tonumber(ARGV[3]) or 0
            if complete == 1 then
                redis.call('SET', KEYS[3], tostring(n))
            else
                redis.call('SET', KEYS[3], tostring(-n))
            end
            return 1
            """, "好友名单回源 CAS 重建"),

    /**
     * 黑名单 Hash 回源：先写同槽临时 Hash，INIT 仍未出现再 RENAME，避免 DEL+PUTALL 空窗。
     * 已有 INIT 则只删临时键，不覆盖线上增量 HPUT。
     * KEYS[1]=hash KEYS[2]=initKey KEYS[3]=tmpHash
     * ARGV[1]=count ARGV[2..]=field,value 交替（value 由调用方按 Redis valueSerializer 传入）
     */
    BLACKLIST_REBUILD_CAS_SCRIPT("1", """
            if redis.call('EXISTS', KEYS[2]) == 1 then
                redis.call('DEL', KEYS[3])
                return 0
            end
            redis.call('DEL', KEYS[3])
            local n = tonumber(ARGV[1]) or 0
            for i = 1, n do
                local base = 1 + (i - 1) * 2
                local field = ARGV[base + 1]
                local val = ARGV[base + 2]
                if field ~= nil and field ~= '' then
                    redis.call('HSET', KEYS[3], field, val)
                end
            end
            if redis.call('EXISTS', KEYS[2]) == 1 then
                redis.call('DEL', KEYS[3])
                return 0
            end
            redis.call('DEL', KEYS[1])
            if redis.call('EXISTS', KEYS[3]) == 1 then
                redis.call('RENAME', KEYS[3], KEYS[1])
            end
            redis.call('SET', KEYS[2], '1')
            return 1
            """, "黑名单回源 CAS 重建"),

    /**
     * 关系 ZSET 增量加入：仅新 member 才 INCR 版本；已存在只改 score 不抬版本。
     * KEYS[1]=zset KEYS[2]=versionKey KEYS[3]=initKey
     * ARGV[1]=score ARGV[2]=member
     */
    RELATION_ROSTER_ADD_SCRIPT("3", """
            local added = redis.call('ZADD', KEYS[1], tonumber(ARGV[1]) or 0, ARGV[2])
            if added == 1 then
                redis.call('INCR', KEYS[2])
                if redis.call('EXISTS', KEYS[3]) == 1 then
                    local n = tonumber(redis.call('GET', KEYS[3]))
                    if n ~= nil and n >= 0 then
                        redis.call('INCR', KEYS[3])
                    end
                end
            end
            return added
            """, "关系名单增量加入"),

    /**
     * 关系 ZSET 带容量加入（同槽 zset/version/init）。
     * KEYS[1]=zset KEYS[2]=versionKey KEYS[3]=initKey
     * ARGV[1]=score ARGV[2]=member ARGV[3]=maxCount（负数不限制）
     * 返回 1=新加入，2=已在名单（只改 score），0=超限未写入。
     */
    RELATION_ROSTER_ADD_IF_CAPACITY_SCRIPT("3", """
            if redis.call('ZSCORE', KEYS[1], ARGV[2]) ~= false then
                redis.call('ZADD', KEYS[1], tonumber(ARGV[1]) or 0, ARGV[2])
                return 2
            end
            local max = tonumber(ARGV[3])
            if max ~= nil and max >= 0 then
                if redis.call('ZCARD', KEYS[1]) >= max then
                    return 0
                end
            end
            redis.call('ZADD', KEYS[1], tonumber(ARGV[1]) or 0, ARGV[2])
            redis.call('INCR', KEYS[2])
            if redis.call('EXISTS', KEYS[3]) == 1 then
                local n = tonumber(redis.call('GET', KEYS[3]))
                if n ~= nil and n >= 0 then
                    redis.call('INCR', KEYS[3])
                end
            end
            return 1
            """, "关系名单带容量加入"),

    /**
     * 关系 ZSET 移除：先 INCR 版本再 ZREM；仅真正删掉且 INIT 为完整正计数时 DECR INIT。
     * KEYS[1]=zset KEYS[2]=versionKey KEYS[3]=initKey
     * ARGV[1]=member
     */
    RELATION_ROSTER_REMOVE_SCRIPT("3", """
            redis.call('INCR', KEYS[2])
            local removed = redis.call('ZREM', KEYS[1], ARGV[1])
            if removed == 1 and redis.call('EXISTS', KEYS[3]) == 1 then
                local n = tonumber(redis.call('GET', KEYS[3]))
                if n ~= nil and n > 0 then
                    redis.call('DECR', KEYS[3])
                end
            end
            return removed
            """, "关系名单移除"),

    /**
     * 已在名单内才改 score，不 INCR 版本、不改 INIT。设管理员/转让群主用，避免扇出扫描被误伤。
     * KEYS[1]=zset  ARGV[1]=score ARGV[2]=member
     * 返回 1=已更新，0=不在名单。
     */
    RELATION_ROSTER_UPDATE_SCORE_SCRIPT("1", """
            if redis.call('ZSCORE', KEYS[1], ARGV[2]) == false then
                return 0
            end
            redis.call('ZADD', KEYS[1], tonumber(ARGV[1]) or 0, ARGV[2])
            return 1
            """, "关系名单改分"),

    /**
     * INIT 与 ZCARD 一致性（只读，不 DEL）。不一致由回源 CAS 覆盖。
     * 正数=完整名单（可负向判定）；负数=截断名单（只读缓存，不可证伪）。
     * KEYS[1]=zset KEYS[2]=initKey
     * 返回 0=缺失或不一致、1=完整一致、2=截断一致。
     */
    RELATION_ROSTER_INIT_CHECK_SCRIPT("2", """
            local init = redis.call('GET', KEYS[2])
            if init == false or init == nil then
                return 0
            end
            local n = tonumber(init)
            if n == nil then
                return 0
            end
            local expected = math.abs(n)
            local zcard = redis.call('ZCARD', KEYS[1])
            if zcard ~= expected then
                return 0
            end
            if n < 0 then
                return 2
            end
            return 1
            """, "关系名单 INIT 一致性校验"),

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
     * 客服 ticket 收消息：packetId 大于本端 ticket sro 时记入未读 ZSET，超限裁最旧。
     * KEYS[1]=urHash KEYS[2]=sroHash KEYS[3]=uridZset
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
            local function normId(v)
                if v == false or v == nil then return '0' end
                v = tostring(v)
                v = string.gsub(v, '^0+', '')
                if v == '' then return '0' end
                return v
            end
            local function packetIdLe(a, b)
                a = normId(a)
                b = normId(b)
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
            redis.call('ZADD', KEYS[3], 0, tostring(pid))
            local nv = toIntOrZero(redis.call('ZCARD', KEYS[3]))
            if nv > storeMax then
                redis.call('ZREMRANGEBYRANK', KEYS[3], 0, nv - storeMax - 1)
                nv = toIntOrZero(redis.call('ZCARD', KEYS[3]))
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
     * 客服 ticket 已读：推进 sro Hash；从未读 ZSET 只移除 {@code <= incomingOffset}，再回写计数。
     * KEYS[1]=urHash KEYS[2]=sroHash KEYS[3]=uridZset
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
            local function normId(v)
                if v == false or v == nil then return '0' end
                v = tostring(v)
                v = string.gsub(v, '^0+', '')
                if v == '' then return '0' end
                return v
            end
            local function packetIdLe(a, b)
                a = normId(a)
                b = normId(b)
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
            local ids = redis.call('ZRANGE', KEYS[3], 0, -1)
            if #ids > 0 then
                for _, id in ipairs(ids) do
                    if packetIdLe(id, inc) then
                        redis.call('ZREM', KEYS[3], id)
                    end
                end
                local left = toIntOrZero(redis.call('ZCARD', KEYS[3]))
                if left == 0 then
                    redis.call('HDEL', KEYS[1], field)
                else
                    redis.call('HSET', KEYS[1], field, left)
                end
            end
            return merged
            """, "客服 ticket 已读清未读"),

    /**
     * appKey 连接配额预占。HASH field=nodeId，整 key 打 {@code {appKey}} 槽，跨节点可原子求和。
     * KEYS[1]=quotaHash ARGV[1]=nodeId ARGV[2]=maxConnections ARGV[3]=ttlSeconds
     */
    APP_KEY_CONN_RESERVE_SCRIPT("1", """
            local max = tonumber(ARGV[2])
            local ttl = tonumber(ARGV[3]) or 0
            local sum = 0
            local vals = redis.call('HVALS', KEYS[1])
            for i = 1, #vals do
              sum = sum + (tonumber(vals[i]) or 0)
            end
            if max ~= nil and max >= 0 and sum >= max then
              return 0
            end
            redis.call('HINCRBY', KEYS[1], ARGV[1], 1)
            if ttl > 0 then
              redis.call('EXPIRE', KEYS[1], ttl)
            end
            return 1
            """, "appKey 连接配额预占"),

    /**
     * 释放本节点一格配额。
     * KEYS[1]=quotaHash ARGV[1]=nodeId
     */
    APP_KEY_CONN_RELEASE_SCRIPT("1", """
            local n = tonumber(redis.call('HINCRBY', KEYS[1], ARGV[1], -1)) or 0
            if n <= 0 then
              redis.call('HDEL', KEYS[1], ARGV[1])
            end
            return 1
            """, "appKey 连接配额释放"),

    /**
     * 好友/群审批处理权：键只有 appKey + requestSessionId，不含设备。
     * 返回 1 首次取得，2 同一 commandId 重试，3 相同动作已在处理或完成，4 相反动作已占用，0 非法。
     * KEYS[1]=hash ARGV[1]=targetProgress ARGV[2]=commandId ARGV[3]=action ARGV[4]=operatorId ARGV[5]=now ARGV[6]=ttlMs
     */
    APPROVAL_PROGRESS_CAS_SCRIPT("1", """
            local progress = redis.call('HGET', KEYS[1], 'progress')
            local commandId = redis.call('HGET', KEYS[1], 'commandId')
            local action = redis.call('HGET', KEYS[1], 'action')
            local function touch()
              local ttl = tonumber(ARGV[6])
              if ttl ~= nil and ttl > 0 then redis.call('PEXPIRE', KEYS[1], ttl) end
            end
            local function nowMillis()
              local now = redis.call('TIME')
              return now[1] * 1000 + math.floor(now[2] / 1000)
            end
            if progress == false or progress == nil or progress == '' or progress == 'JOINING' then
              redis.call('HSET', KEYS[1],
                'progress', ARGV[1],
                'commandId', ARGV[2],
                'action', ARGV[3],
                'operatorId', ARGV[4],
                'processingAt', ARGV[5])
              touch()
              return 1
            end
            if commandId == ARGV[2] then
              touch()
              return 2
            end
            if progress == 'AGREEING' or progress == 'REFUSING' then
              local processingAt = tonumber(redis.call('HGET', KEYS[1], 'processingAt'))
              local lease = tonumber(ARGV[7])
              local expired = processingAt ~= nil and lease ~= nil and lease > 0 and (nowMillis() - processingAt) > lease
              if action == ARGV[3] and expired then
                redis.call('HSET', KEYS[1],
                  'progress', ARGV[1],
                  'commandId', ARGV[2],
                  'action', ARGV[3],
                  'operatorId', ARGV[4],
                  'processingAt', ARGV[5])
                touch()
                return 1
              end
              if action == ARGV[3] then return 5 end
              return 4
            end
            if progress == 'APPROVED' or progress == 'REJECTED' then
              if action == ARGV[3] then return 3 end
              return 4
            end
            return 0
            """, "审批进度 CAS"),

    /**
     * 只有持有 commandId 且动作一致时才能写成终态。
     * KEYS[1]=hash ARGV[1]=commandId ARGV[2]=action ARGV[3]=terminal ARGV[4]=ttlMs
     * 返回 1 成功，0 不匹配。
     */
    APPROVAL_PROGRESS_FINISH_SCRIPT("1", """
            local commandId = redis.call('HGET', KEYS[1], 'commandId')
            local action = redis.call('HGET', KEYS[1], 'action')
            local progress = redis.call('HGET', KEYS[1], 'progress')
            if commandId ~= ARGV[1] or action ~= ARGV[2] then return 0 end
            if progress == ARGV[3] then return 1 end
            if progress ~= 'AGREEING' and progress ~= 'REFUSING' then return 0 end
            redis.call('HSET', KEYS[1], 'progress', ARGV[3])
            local ttl = tonumber(ARGV[4])
            if ttl ~= nil and ttl > 0 then redis.call('PEXPIRE', KEYS[1], ttl) end
            return 1
            """, "审批终态 CAS"),

    /**
     * 毒消息隔离前释放本命令占用。相反命令或已终态不动。
     * KEYS[1]=hash ARGV[1]=commandId
     */
    APPROVAL_PROGRESS_RELEASE_SCRIPT("1", """
            local commandId = redis.call('HGET', KEYS[1], 'commandId')
            local progress = redis.call('HGET', KEYS[1], 'progress')
            if commandId ~= ARGV[1] then return 0 end
            if progress ~= 'AGREEING' and progress ~= 'REFUSING' then return 0 end
            return redis.call('DEL', KEYS[1])
            """, "审批处理权释放"),

    /**
     * 心跳：把本节点 field 写成本地真实计数，并删掉已不在租约里的节点 field。
     * KEYS=quotaHash,seenHash；ARGV=nodeId, localCount, ttlSeconds, staleSeconds, liveNodeId...
     */
    APP_KEY_CONN_SYNC_SCRIPT("1", """
            local nodeId = ARGV[1]
            local count = tonumber(ARGV[2]) or 0
            local ttl = tonumber(ARGV[3]) or 0
            local stale = tonumber(ARGV[4]) or 0
            local clock = redis.call('TIME')
            local now = tonumber(clock[1])
            if #ARGV >= 5 then
              local live = {}
              for i = 5, #ARGV do
                live[ARGV[i]] = true
              end
              local fields = redis.call('HKEYS', KEYS[1])
              for i = 1, #fields do
                if fields[i] ~= nodeId and live[fields[i]] ~= true then
                  local seen = tonumber(redis.call('HGET', KEYS[2], fields[i]))
                  if seen and stale > 0 and now - seen >= stale then
                    redis.call('HDEL', KEYS[1], fields[i])
                    redis.call('HDEL', KEYS[2], fields[i])
                  elseif not seen then
                    redis.call('HSET', KEYS[2], fields[i], now)
                  end
                end
              end
            end
            if count <= 0 then
              redis.call('HDEL', KEYS[1], nodeId)
              redis.call('HDEL', KEYS[2], nodeId)
            else
              redis.call('HSET', KEYS[1], nodeId, count)
              redis.call('HSET', KEYS[2], nodeId, now)
            end
            if ttl > 0 and redis.call('EXISTS', KEYS[1]) == 1 then
              redis.call('EXPIRE', KEYS[1], ttl)
              redis.call('EXPIRE', KEYS[2], ttl)
            end
            return 1
            """, "appKey 连接配额心跳对齐");

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
