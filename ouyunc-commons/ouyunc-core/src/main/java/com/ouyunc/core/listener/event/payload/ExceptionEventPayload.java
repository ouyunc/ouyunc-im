package com.ouyunc.core.listener.event.payload;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.ExceptionSeverity;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import org.apache.commons.lang3.StringUtils;

/**
 * 异常事件唯一载体（不含堆栈、不含完整 Packet）。
 */
public record ExceptionEventPayload(
        ExceptionSeverity severity,
        ExceptionCodeEnum code,
        String message,
        String scene,
        Long packetId,
        Byte messageType,
        String appKey,
        String from,
        String to,
        String causeType
) {

    public static ExceptionEventPayload business(ExceptionCodeEnum code, String message, String scene, Packet packet) {
        return of(ExceptionSeverity.BUSINESS, code, message, scene, packet, null);
    }

    public static ExceptionEventPayload system(ExceptionCodeEnum code, String message, String scene,
                                               Packet packet, Throwable cause) {
        return of(ExceptionSeverity.SYSTEM, code, message, scene, packet, cause);
    }

    public static ExceptionEventPayload pipeline(String scene, Throwable cause) {
        return of(ExceptionSeverity.PIPELINE, ExceptionCodeEnum.UNKNOWN_ERROR, null, scene, null, cause);
    }

    public static ExceptionEventPayload of(ExceptionSeverity severity, ExceptionCodeEnum code, String message,
                                           String scene, Packet packet, Throwable cause) {
        ExceptionSeverity sev = severity != null ? severity : ExceptionSeverity.SYSTEM;
        ExceptionCodeEnum resolvedCode = code != null ? code : ExceptionCodeEnum.UNKNOWN_ERROR;
        String msg = StringUtils.isNotBlank(message) ? message : resolvedCode.getMessage();
        if (StringUtils.isBlank(msg) && cause != null) {
            msg = cause.getMessage();
        }
        if (StringUtils.isBlank(msg)) {
            msg = resolvedCode.getMessage();
        }
        PacketBrief brief = PacketBrief.from(packet);
        String causeType = cause == null ? null : cause.getClass().getName();
        return new ExceptionEventPayload(
                sev,
                resolvedCode,
                msg,
                StringUtils.defaultIfBlank(scene, "unknown"),
                brief.packetId(),
                brief.messageType(),
                brief.appKey(),
                brief.from(),
                brief.to(),
                causeType
        );
    }

    private record PacketBrief(Long packetId, Byte messageType, String appKey, String from, String to) {
        static PacketBrief from(Packet packet) {
            if (packet == null) {
                return new PacketBrief(null, null, null, null, null);
            }
            Long packetId = packet.getPacketId() > 0 ? packet.getPacketId() : null;
            Byte messageType = packet.getMessageType();
            Message message = packet.getMessage();
            if (message == null) {
                return new PacketBrief(packetId, messageType, null, null, null);
            }
            Metadata metadata = message.getMetadataOrNull();
            String appKey = metadata != null ? metadata.getIngress().getAppKey() : null;
            return new PacketBrief(packetId, messageType, appKey, message.getFrom(), message.getTo());
        }
    }
}
