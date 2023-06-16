package com.ouyunc.im.packet.message.content;

import com.alibaba.fastjson2.annotation.JSONField;
import com.ouyunc.im.serialize.Long2StringSerializer;

import java.io.Serializable;

/**
 * 服务端预警消息内容，可以改成服务端推送的内容，后面优化该
 */
public class ServerWarningContent  implements Serializable {
    private static final long serialVersionUID = 100008L;

    /**
     * 有问题的消息包id
     */
    @JSONField(serializeUsing = Long2StringSerializer.class)
    private long packetId;

    /**
     * 警告消息
     */
    private String warningMsg;

    public long getPacketId() {
        return packetId;
    }

    public void setPacketId(long packetId) {
        this.packetId = packetId;
    }

    public String getWarningMsg() {
        return warningMsg;
    }

    public void setWarningMsg(String warningMsg) {
        this.warningMsg = warningMsg;
    }
}
