package com.ouyunc.base.model;

import com.ouyunc.base.packet.message.content.MessageSendResultContent;

import java.io.Serial;
import java.util.List;

/** HTTP 推送与长连接共用单条受理结果字段；批量推送额外携带逐接收人结果。 */
public class MessagePushResponse extends MessageSendResultContent {
    @Serial
    private static final long serialVersionUID = 1L;

    /** toList 扇出时每接收人结果；单推为 null。 */
    private List<MessagePushResponse> items;

    public List<MessagePushResponse> getItems() {
        return items;
    }

    public void setItems(List<MessagePushResponse> items) {
        this.items = items;
    }
}
