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
     * 撤回消息lua 脚本
     */
    public static final String WITHDRAW_MESSAGE_LUA_SCRIPT = "";
}
