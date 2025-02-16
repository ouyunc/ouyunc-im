package com.ouyunc.base.constant;

/**
 * lua 脚本
 */
public class LuaScriptConstant {

    /**
     * 一对一保存消息(包含离线消息)lua 脚本
     */
    public static final String SAVE_MESSAGE_WITH_OFFLINE_LUA_SCRIPT = "local business_save_success = false\n" +
            "if tonumber(ARGV[2]) > 0 then\n" +
            "    local set_result = redis.call('SET', KEYS[1], ARGV[1], 'PX', tonumber(ARGV[2]))\n" +
            "    business_save_success = set_result == 'OK'\n" +
            "else\n" +
            "    local set_result = redis.call('SET', KEYS[1], ARGV[1])\n" +
            "    business_save_success = set_result == 'OK'\n" +
            "end\n" +
            "local offline_save_success = redis.call('ZADD', KEYS[2], tonumber(ARGV[4]), ARGV[3]) > 0\n" +
            "local session_save_success = redis.call('ZADD', KEYS[3], tonumber(ARGV[6]), ARGV[5]) > 0\n" +
            "local overall_success = business_save_success and offline_save_success and session_save_success\n" +
            "return overall_success and 1 or 0";

    /**
     * 一对一保存消息lua 脚本
     */
    public static final String SAVE_MESSAGE_LUA_SCRIPT = "local expire_time = tonumber(ARGV[2])\n" +
            "return ((expire_time > 0) and \n" +
            "        redis.call('SET', KEYS[1], ARGV[1], 'PX', expire_time) == 'OK' or\n" +
            "        redis.call('SET', KEYS[1], ARGV[1]) == 'OK') and\n" +
            "       redis.call('ZADD', KEYS[2], tonumber(ARGV[4]), ARGV[3]) > 0";
}
