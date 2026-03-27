package com.ouyunc.message.listener;

import com.ouyunc.base.constant.enums.LuaScriptEnum;
import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.core.engine.ReactiveRedisLuaScriptEngine;
import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 预加载Lua脚本监听器
 */
public class PreloadedLuaScriptListener implements MessageListener<MessageEvent> {
    private static final Logger log = LoggerFactory.getLogger(PreloadedLuaScriptListener.class);

    /**
     * 将lua 脚本sha 值缓存到本地缓存中, 需要注意redis 集群模式下，在各节点的同步
     */
    @Override
    public EventType type() {
        return MessageEventTypeEnum.PRELOAD_LUA_SCRIPT;
    }

    @Override
    public void onEvent(MessageEvent event) {
        if (event.getType() != MessageEventTypeEnum.PRELOAD_LUA_SCRIPT) {
            return;
        }
        if (event.getSource() instanceof LuaScriptEnum[] luaScripts) {
            for (LuaScriptEnum luaScript : luaScripts) {
                log.debug("预加载lua脚本: {}", luaScript.getScript());
                ReactiveRedisLuaScriptEngine.preloadLuaScript(luaScript);
            }
        }

    }
}
