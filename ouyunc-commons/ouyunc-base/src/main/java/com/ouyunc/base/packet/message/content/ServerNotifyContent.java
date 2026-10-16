package com.ouyunc.base.packet.message.content;

import java.io.Serial;
import java.io.Serializable;

/**
 * 给人看的服务端通知正文，例如空闲提示、踢下线、节点维护。
 * 只放一句展示文案，不承载客服事件或群操作。
 */
public class ServerNotifyContent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 展示给用户的一句文案 */
    private String notify;

    public String getNotify() {
        return notify;
    }

    public void setNotify(String notify) {
        this.notify = notify;
    }

    public ServerNotifyContent() {
    }

    public ServerNotifyContent(String notify) {
        this.notify = notify;
    }
}
