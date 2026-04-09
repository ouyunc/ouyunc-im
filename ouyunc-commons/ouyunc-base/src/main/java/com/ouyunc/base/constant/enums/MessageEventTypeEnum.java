package com.ouyunc.base.constant.enums;

/**
 * 统一消息事件类型（与 {@code MessageEvent#getType()} 对应；原各 *Event 子类由 type + source 载体区分）。
 */
public enum MessageEventTypeEnum implements EventType {

    /** source: {@code ClientLoginEventPayload} */
    CLIENT_LOGIN(1, "客户端登录事件"),
    /** source: {@code LoginClientInfo | MqttLoginClientInfo} */
    CLIENT_LOGOUT(2, "客户端登出事件"),
    /** source: {@code String(serverAddress)} */
    SERVER_STARTUP(3, "服务启动事件"),
    /** source: {@code MessageServer | Object} */
    SERVER_STOP(4, "服务停止事件"),
    /** source: {@code String(serverAddress)} */
    SERVER_OFFLINE(5, "集群节点离线事件"),
    /** source: {@code LuaScriptEnum[]} */
    PRELOAD_LUA_SCRIPT(6, "预加载Lua脚本事件"),
    /** source: {@code SendResult} */
    SEND_FAIL(7, "消息发送失败事件"),
    /** source: {@code Packet} */
    SAVE_MESSAGE(8, "消息持久化事件"),
    /** source: {@code Packet} */
    SEND_OFFLINE(9, "发送离线消息事件"),
    /** source: {@code Packet} */
    REMOVE_OFFLINE(10, "移除离线消息事件"),
    /** source: {@code Packet | BusinessPayload} */
    WITHDRAW_MESSAGE(11, "撤回消息事件"),
    /** source: {@code ExceptionEventPayload | Throwable} */
    EXCEPTION(12, "异常事件"),
    /** source: {@code MessageEvent(type=EXCEPTION)} */
    EXCEPTION_PERSIST(13, "异常持久化事件"),
    /** source: {@code Packet} */
    ON_MESSAGE(14, "客户端收消息事件"),
    /** source: {@code LoginClientInfo} */
    CLIENT_KEEP_ALIVE_REFRESH(15, "客户端登录保活刷新事件"),
    ;

    private final int type;

    private final String name;

    MessageEventTypeEnum(int type, String name) {
        this.type = type;
        this.name = name;
    }

    @Override
    public Integer getType() {
        return type;
    }

    public String getName() {
        return name;
    }
}
