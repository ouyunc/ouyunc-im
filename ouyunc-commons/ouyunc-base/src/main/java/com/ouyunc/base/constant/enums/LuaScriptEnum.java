package com.ouyunc.base.constant.enums;

/**
 * lua 脚本枚举
 */
public enum LuaScriptEnum {

    BATCH_SCORE_LUA_SCRIPT("1","local scores = {}\n" +
            "for i = 1, #ARGV do\n" +
            "    local member = ARGV[i]\n" +
            "    local score = redis.call('ZSCORE', KEYS[1], member)\n" +
            "    scores[i] = score\n" +
            "end\n" +
            "return scores", "zset批量获取分数lua脚本"),

    SAVE_MESSAGE_WITH_OFFLINE_LUA_SCRIPT("1","-- KEYS[1]: SET操作的键\n" +
            "-- KEYS[2]: 第一个ZADD的键（离线消息）\n" +
            "-- KEYS[3]: 第二个ZADD的键（会话消息）\n" +
            "-- ARGV[1]: 序列化的Packet值\n" +
            "-- ARGV[2]: 过期时间（毫秒，0表示不过期）\n" +
            "-- ARGV[3]: Packet ID\n" +
            "-- ARGV[4]: 服务器时间（分数）\n" +
            "\n" +
            "local setResult\n" +
            "local expireTime = tonumber(ARGV[2])\n " +
            "local score = tonumber(ARGV[4])\n " +
            "if expireTime > 0 then\n" +
            "    setResult = redis.call('SET', KEYS[1], ARGV[1], 'PX', expireTime)\n" +
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
            "local zadd1 = redis.call('ZADD', KEYS[2], score, ARGV[3])\n" +
            "if zadd1 ~= 1  then\n" +
            "    redis.call('DEL', KEYS[1])\n" +
            "    return false\n" +
            "end\n" +
            "\n" +
            "local zadd2 = redis.call('ZADD', KEYS[3], score, ARGV[3])\n" +
            "if zadd2 ~= 1 then\n" +
            "    redis.call('DEL', KEYS[1])\n" +
            "    redis.call('ZREM', KEYS[2], ARGV[3])\n" +
            "    return false\n" +
            "end\n" +
            "\n" +
            "return true","一对一保存消息(包含离线消息)lua 脚本"),

    SAVE_MESSAGE_WITHOUT_OFFLINE_LUA_SCRIPT("1","-- KEYS[1]: SET操作的键\n" +
            "-- KEYS[2]: 第二个ZADD的键（会话消息）\n" +
            "-- ARGV[1]: 序列化的Packet值\n" +
            "-- ARGV[2]: 过期时间（毫秒，0表示不过期）\n" +
            "-- ARGV[3]: Packet ID\n" +
            "-- ARGV[4]: 服务器时间（分数）\n" +
            "\n" +
            "local setResult\n" +
            "local expireTime =  tonumber(ARGV[2])\n" +
            "if expireTime > 0 then\n" +
            "    setResult = redis.call('SET', KEYS[1], ARGV[1], 'PX', expireTime)\n" +
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
            "return true","一对一保存消息lua 脚本"),


    BATCH_SAVE_MESSAGE_LUA_SCRIPT("1","--[[\n" +
            "KEYS 与 ARGV 参数同上\n" +
            "--]]\n" +
            "\n" +
            "local msgKey = KEYS[1]\n" +
            "local sessionKey = KEYS[2]\n" +
            "local offlineKeys = {unpack(KEYS, 3, #KEYS)}\n" +
            "local expireTime = tonumber(ARGV[1])\n" +
            "local packet = ARGV[2]\n" +
            "local serverTime = tonumber(ARGV[3])\n" +
            "local packetId = ARGV[4]\n" +
            "\n" +
            "local rollbackOps = {}\n" +
            "\n" +
            "-- 定义回滚函数\n" +
            "local function rollback()\n" +
            "    for i = #rollbackOps, 1, -1 do\n" +
            "        local op = rollbackOps[i]\n" +
            "        redis.call(unpack(op))\n" +
            "    end\n" +
            "end\n" +
            "\n" +
            "-- 1. 设置消息\n" +
            "local setResult\n" +
            "if expireTime > 0 then\n" +
            "    setResult = redis.call('SET', msgKey, packet, 'PX', expireTime)\n" +
            "else\n" +
            "    setResult = redis.call('SET', msgKey, packet)\n" +
            "end\n" +
            "\n" +
            "if type(setResult) == 'string' and setResult ~= 'OK' then\n" +
            "    return false\n" +
            "elseif type(setResult) == 'table' and setResult['ok'] ~= 'OK' then\n" +
            "    return false\n" +
            "end\n" +
            "table.insert(rollbackOps, {'DEL', msgKey})\n" +
            "\n" +
            "-- 2. 添加会话消息\n" +
            "local zaddSession = redis.call('ZADD', sessionKey, serverTime, packetId)\n" +
            "if zaddSession  ~= 1 then\n" +
            "    rollback()\n" +
            "    return false\n" +
            "end\n" +
            "table.insert(rollbackOps, {'ZREM', sessionKey, packetId})\n" +
            "\n" +
            "-- 3. 批量添加离线消息\n" +
            "for _, key in ipairs(offlineKeys) do\n" +
            "    local zaddOffline = redis.call('ZADD', key, serverTime, packetId)\n" +
            "    if zaddOffline ~= 1 then\n" +
            "        rollback()\n" +
            "        return false\n" +
            "    end\n" +
            "    table.insert(rollbackOps, {'ZREM', key, packetId})\n" +
            "end\n" +
            "\n" +
            "return true","批量保存消息lua 脚本，针对群聊"),



    BATCH_WITHDRAW_MESSAGE_LUA_SCRIPT("1","local groupUsersCount = tonumber(KEYS[1])\n" +
            "local keys = {unpack(KEYS, 2, #KEYS)}\n" +
            "local keysPerItem = 2 + groupUsersCount\n" +
            "local numArgs = #ARGV\n" +
            "local hasError = false\n" +
            "\n" +
            "-- 参数校验：检查 KEYS 数量是否匹配\n" +
            "if #keys ~= numArgs * keysPerItem then\n" +
            "    return false\n" +
            "end\n" +
            "\n" +
            "for i = 1, numArgs do\n" +
            "    local base = (i - 1) * keysPerItem\n" +
            "    local messageKey = keys[base + 1]\n" +
            "    local sessionKey = keys[base + 2]\n" +
            "    local value = ARGV[i]\n" +
            "\n" +
            "    -- 使用 pcall 捕获命令执行错误\n" +
            "    local ok\n" +
            "\n" +
            "    -- 1. 删除消息键\n" +
            "    ok = pcall(redis.call, \"DEL\", messageKey)\n" +
            "    if not ok then hasError = true end\n" +
            "\n" +
            "    -- 2. 从会话集合移除消息\n" +
            "    ok = pcall(redis.call, \"ZREM\", sessionKey, value)\n" +
            "    if not ok then hasError = true end\n" +
            "\n" +
            "    -- 3. 从所有离线队列移除消息\n" +
            "    for j = 1, groupUsersCount do\n" +
            "        local offlineKey = keys[base + 2 + j]\n" +
            "        ok = pcall(redis.call, \"ZREM\", offlineKey, value)\n" +
            "        if not ok then hasError = true end\n" +
            "    end\n" +
            "end\n" +
            "\n" +
            "-- 返回最终状态（Redis 会将 true 转为 1，false 转为 nil）\n" +
            "return not hasError","批量撤销消息lua脚本"),

    BATCH_READ_RECEIPT_MESSAGE_LUA_SCRIPT("1","-- 假设 KEYS 数组中，奇数索引位置为要删除的 key，偶数索引位置为哈希表的 key\n" +
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
            "return result","批量读已回执消息lua 脚本"),

    ;

    private String version;

    private String script;

    private String description;


    LuaScriptEnum(String version, String script, String description) {
        this.script = script;
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
