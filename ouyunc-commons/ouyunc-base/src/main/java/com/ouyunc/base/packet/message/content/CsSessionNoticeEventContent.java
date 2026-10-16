package com.ouyunc.base.packet.message.content;

import java.io.Serial;

/**
 * 只有一句系统提示的会话事件，例如进入托管、托管槽已满。
 */
public class CsSessionNoticeEventContent extends CsSessionEventContent {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 给接收方看的系统提示，不是聊天正文。 */
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = normalize(message);
    }
}
