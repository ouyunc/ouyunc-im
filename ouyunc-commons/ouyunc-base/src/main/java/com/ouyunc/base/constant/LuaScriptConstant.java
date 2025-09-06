package com.ouyunc.base.constant;

/**
 * lua 脚本
 */
public class LuaScriptConstant {
    public static final String BATCH_WITHDRAW_MESSAGE_LUA_SCRIPT =
                    "local groupUsersCount = tonumber(KEYS[1])\n" +
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
                            "return not hasError";



    /**
     * zset批量获取分数lua脚本
     */
    public static final String BATCH_SCORE_LUA_SCRIPT =
            "local scores = {}\n" +
                    "for i = 1, #ARGV do\n" +
                    "    local member = ARGV[i]\n" +
                    "    local score = redis.call('ZSCORE', KEYS[1], member)\n" +
                    "    scores[i] = score\n" +
                    "end\n" +
                    "return scores";
}
