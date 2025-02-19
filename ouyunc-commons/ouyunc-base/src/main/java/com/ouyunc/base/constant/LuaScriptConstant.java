package com.ouyunc.base.constant;

/**
 * lua 脚本
 */
public class LuaScriptConstant {

    /**
     * 一对一保存消息(包含离线消息)lua 脚本
     */
    public static final String SAVE_MESSAGE_WITH_OFFLINE_LUA_SCRIPT = "-- KEYS[1]: SET操作的键\n" +
            "-- KEYS[2]: 第一个ZADD的键（离线消息）\n" +
            "-- KEYS[3]: 第二个ZADD的键（会话消息）\n" +
            "-- ARGV[1]: 序列化的Packet值\n" +
            "-- ARGV[2]: 过期时间（毫秒，0表示不过期）\n" +
            "-- ARGV[3]: Packet ID\n" +
            "-- ARGV[4]: 服务器时间（分数）\n" +
            "\n" +
            "local setResult\n" +
            "if tonumber(ARGV[2]) > 0 then\n" +
            "    setResult = redis.call('SET', KEYS[1], ARGV[1], 'PX', tonumber(ARGV[2]))\n" +
            "else\n" +
            "    setResult = redis.call('SET', KEYS[1], ARGV[1])\n" +
            "end\n" +
            "\n" +
            "if type(setResult) == 'string' and setResult ~= 'OK' then\n" +
            "    return false\n" +
            "elseif type(setResult) == 'table' and setResult['ok'] ~= 'OK' then\n" +
            "    return false\n" +
            "end\n" +
            "\n" +
            "local zadd1 = redis.call('ZADD', KEYS[2], tonumber(ARGV[4]), ARGV[3])\n" +
            "if zadd1 ~= 1 then\n" +
            "    redis.call('DEL', KEYS[1])\n" +
            "    return false\n" +
            "end\n" +
            "\n" +
            "local zadd2 = redis.call('ZADD', KEYS[3], tonumber(ARGV[4]), ARGV[3])\n" +
            "if zadd2 ~= 1 then\n" +
            "    redis.call('DEL', KEYS[1])\n" +
            "    redis.call('ZREM', KEYS[2], ARGV[3])\n" +
            "    return false\n" +
            "end\n" +
            "\n" +
            "return true";;

    /**
     * 一对一保存消息lua 脚本
     */
    public static final String SAVE_MESSAGE_WITHOUT_OFFLINE_LUA_SCRIPT = "-- KEYS[1]: SET操作的键\n" +
            "-- KEYS[2]: 第二个ZADD的键（会话消息）\n" +
            "-- ARGV[1]: 序列化的Packet值\n" +
            "-- ARGV[2]: 过期时间（毫秒，0表示不过期）\n" +
            "-- ARGV[3]: Packet ID\n" +
            "-- ARGV[4]: 服务器时间（分数）\n" +
            "\n" +
            "local setResult\n" +
            "if tonumber(ARGV[2]) > 0 then\n" +
            "    setResult = redis.call('SET', KEYS[1], ARGV[1], 'PX', tonumber(ARGV[2]))\n" +
            "else\n" +
            "    setResult = redis.call('SET', KEYS[1], ARGV[1])\n" +
            "end\n" +
            "\n" +
            "if type(setResult) == 'string' and setResult ~= 'OK' then\n" +
            "    return false\n" +
            "elseif type(setResult) == 'table' and setResult['ok'] ~= 'OK' then\n" +
            "    return false\n" +
            "end\n" +
            "\n" +
            "local zadd1 = redis.call('ZADD', KEYS[2], tonumber(ARGV[4]), ARGV[3])\n" +
            "if zadd1 ~= 1 then\n" +
            "    redis.call('DEL', KEYS[1])\n" +
            "    return false\n" +
            "end\n" +
            "return true";



    /**
     * 批量保存消息lua 脚本，针对群聊
     */
    public static final String BATCH_SAVE_MESSAGE_LUA_SCRIPT =
            "local key1 = KEYS[1]\n" +
            "local key3 = KEYS[2]\n" +
            "local value1 = ARGV[1]\n" +
            "local expireTime = tonumber(ARGV[2])\n" +
            "local value2 = ARGV[3]\n" +
            "local score = tonumber(ARGV[4])\n" +
            "local groupUserCount = tonumber(ARGV[5])\n" +
            "\n" +
            "-- 参数有效性校验\n" +
            "if not expireTime or not score or not groupUserCount or groupUserCount < 0 then\n" +
            "    return false\n" +
            "end\n" +
            "\n" +
            "local rollbackSteps = {}\n" +
            "local function rollback()\n" +
            "    redis.call('DEL', key1)\n" +
            "    for _, groupUser in ipairs(rollbackSteps) do\n" +
            "        redis.call('ZREM', groupUser, value2)\n" +
            "    end\n" +
            "    redis.call('ZREM', key3, value2)\n" +
            "end\n" +
            "\n" +
            "-- 设置key1（带过期时间判断）\n" +
            "local ok\n" +
            "if expireTime > 0 then\n" +
            "    ok = redis.call('SET', key1, value1, 'PX', expireTime)\n" +
            "else\n" +
            "    ok = redis.call('SET', key1, value1)\n" +
            "end\n" +
            "if type(ok) == 'string' and ok  ~= 'OK'  then return false\n" +
            "elseif type(ok) == 'table' and ok['ok'] ~= 'OK' then return false\n" +
            "end\n" +
            "\n" +
            "-- 批量添加群组用户ZSet\n" +
            "for i = 1, groupUserCount do\n" +
            "    local groupUser = ARGV[5+i]\n" +
            "    local added = redis.call('ZADD', groupUser, score, value2)\n" +
            "    if type(added) ~= 'number' then\n" +
            "        rollback()\n" +
            "        return false\n" +
            "    end\n" +
            "    table.insert(rollbackSteps, groupUser)\n" +
            "end\n" +
            "\n" +
            "-- 添加会话消息ZSet\n" +
            "local added = redis.call('ZADD', key3, score, value2)\n" +
            "if type(added) ~= 'number' then\n" +
            "    rollback()\n" +
            "    return false\n" +
            "end\n" +
            "\n" +
            "return true" ;


    /**
     * 批量撤回消息lua 脚本
     */
    public static final String BATCH_WITHDRAW_MESSAGE_LUA_SCRIPT = "local all_success = true\n" +
            "for i = 1, #ARGV, 3 do\n" +
            "    local key1 = KEYS[i]\n" +
            "    local key2 = KEYS[i + 1]\n" +
            "    local key3 = KEYS[i + 2]\n" +
            "    local value = ARGV[i / 3 + 1]\n" +
            "    local success, _ = pcall(function()\n" +
            "        redis.call('DEL', key1)\n" +
            "        redis.call('ZREM', key2, value)\n" +
            "        redis.call('ZREM', key3, value)\n" +
            "    end)\n" +
            "    if not success then\n" +
            "        all_success = false\n" +
            "    end\n" +
            "end\n" +
            "return all_success";



    /**
     * 批量读已回执消息lua 脚本
     */
    public static final String BATCH_READ_RECEIPT_MESSAGE_LUA_SCRIPT ="-- 假设 KEYS 数组中，奇数索引位置为要删除的 key，偶数索引位置为哈希表的 key\n" +
            "-- ARGV 数组中，每三个元素一组，分别为 field2、value2 和 expireTime\n" +
            "local result = true\n" +
            "for i = 1, #KEYS, 2 do\n" +
            "    local key = KEYS[i]\n" +
            "    local key2 = KEYS[i + 1]\n" +
            "    local field2_index = ((i + 1) / 2 - 1) * 3 + 1\n" +
            "    local field2 = ARGV[field2_index]\n" +
            "    local value2 = ARGV[field2_index + 1]\n" +
            "    local expireTime = tonumber(ARGV[field2_index + 2])\n" +
            "\n" +
            "    -- 删除键\n" +
            "    local del_result = redis.call('DEL', key)\n" +
            "\n" +
            "    -- 向哈希表中添加字段值对\n" +
            "    local hset_result = redis.call('HSET', key2, field2, value2)\n" +
            "    if hset_result == nil then\n" +
            "        result = false\n" +
            "    end\n" +
            "\n" +
            "    -- 为哈希表设置过期时间\n" +
            "    local expire_result = redis.call('PEXPIRE', key2, expireTime)\n" +
            "    if expire_result == nil or expire_result == 0 then\n" +
            "        result = false\n" +
            "    end\n" +
            "end\n" +
            "\n" +
            "return result";
}
