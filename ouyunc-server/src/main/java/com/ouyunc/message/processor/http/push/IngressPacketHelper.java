package com.ouyunc.message.processor.http.push;

import com.ouyunc.base.constant.enums.IngressSourceEnum;
import com.ouyunc.base.constant.enums.MessageFromToTypeEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;

/**
 * HTTP 推送 Packet 判定工具。
 */
public final class IngressPacketHelper {

    private IngressPacketHelper() {
    }

    public static boolean isHttpPush(Packet packet) {
        if (packet == null || packet.getMessage() == null) {
            return false;
        }
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return metadata != null && IngressSourceEnum.isHttpPush(metadata.getIngressSource());
    }

    public static boolean isSystemLikeSender(Message message) {
        if (message == null) {
            return false;
        }
        int fromType = message.getFromType();
        return fromType == MessageFromToTypeEnum.SYSTEM.getType()
                || fromType == MessageFromToTypeEnum.BOT.getType();
    }
}
