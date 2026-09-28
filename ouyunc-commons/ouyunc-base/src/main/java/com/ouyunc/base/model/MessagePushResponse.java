package com.ouyunc.base.model;

import com.ouyunc.base.packet.message.content.MessageSubmissionResponseContent;

import java.io.Serial;
import java.util.List;

/** HTTP 推送与长连接共用单条提交受理响应字段；批量推送额外携带逐接收人响应。 */
public class MessagePushResponse extends MessageSubmissionResponseContent {
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
