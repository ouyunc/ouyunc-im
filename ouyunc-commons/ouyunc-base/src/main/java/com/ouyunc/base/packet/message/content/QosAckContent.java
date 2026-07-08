package com.ouyunc.base.packet.message.content;

import java.io.Serializable;

/**
 * @Author fzx
 * @Description: qos ack 内容
 **/
public class QosAckContent implements Serializable {
    private static final long serialVersionUID = -1L;

    /**
     * 服务端原始消息的 packetId（十进制字符串，对外协议）
     */
    private String ackId;

    /**
     * 客户端消息的id
     */
    private String messageId;


    public QosAckContent(String ackId, String messageId) {
        this.ackId = ackId;
        this.messageId = messageId;
    }

    public String getAckId() {
        return ackId;
    }

    public void setAckId(String ackId) {
        this.ackId = ackId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}
