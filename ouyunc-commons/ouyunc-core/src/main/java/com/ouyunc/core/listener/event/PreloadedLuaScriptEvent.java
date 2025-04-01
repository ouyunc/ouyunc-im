package com.ouyunc.core.listener.event;

/**
 * @Author fzx
 * @Description: 预加载lua脚本事件
 **/
public class PreloadedLuaScriptEvent extends MessageEvent {

    public PreloadedLuaScriptEvent(Object source) {
        super(source);
    }

    public PreloadedLuaScriptEvent(Object source, long publishTime) {
        super(source, publishTime);
    }

}
