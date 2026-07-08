package com.ouyunc.core.engine;


import com.ouyunc.base.constant.enums.LuaScriptEnum;

/**
 * todo 响应式RedisLuaScriptEngine 引擎 用来处理和预加载lua 脚本
 */
public class ReactiveRedisLuaScriptEngine {


    /**
     * 预加载lua脚本  保存在 MessageServerContext.luaScriptShaCache  缓存中，每次服务启动时都会执行一次，如果已经存在进行覆盖，保证使用最新的脚本
     * @param luaScript
     */
    public static void preloadLuaScript(LuaScriptEnum luaScript) {
        // 预加载lua脚本
        // todo 预加载lua脚本
    }

}