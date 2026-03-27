package com.ouyunc.base.constant.enums;

/**
 * 统一消息事件类型（与 {@code MessageEvent#getType()} 对应；原各 *Event 子类由 type + source 载体区分）。
 */
public enum MessageEventTypeEnum implements EventType {

    CLIENT_LOGIN(1, "客户端登录事件"),
    CLIENT_LOGOUT(2, "客户端登出事件"),
    SERVER_STARTUP(3, "服务启动事件"),
    SERVER_STOP(4, "服务停止事件"),
    SERVER_OFFLINE(5, "集群节点离线事件"),
    PRELOAD_LUA_SCRIPT(6, "预加载Lua脚本事件"),
    SEND_FAIL(7, "消息发送失败事件"),
    SAVE_MESSAGE(8, "消息持久化事件"),
    SEND_OFFLINE(9, "发送离线消息事件"),
    REMOVE_OFFLINE(10, "移除离线消息事件"),
    WITHDRAW_MESSAGE(11, "撤回消息事件"),
    EXCEPTION(12, "异常事件"),
    EXCEPTION_PERSIST(13, "异常持久化事件"),
    ON_MESSAGE(14, "客户端收消息事件"),
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
