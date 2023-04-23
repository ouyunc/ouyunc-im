package com.ouyunc.im.packet.message.content;

import java.io.Serializable;

/**
 * @author fzx
 * 服务端发送的通知内容，可以对其进行扩展
 */
public class ServerNotifyContent implements Serializable {
    private static final long serialVersionUID = 100010L;

    /**
     * 通知内容
     */
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
