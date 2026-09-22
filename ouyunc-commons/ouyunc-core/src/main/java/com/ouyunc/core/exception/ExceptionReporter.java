package com.ouyunc.core.exception;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.ExceptionSeverity;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.model.ExceptionRecord;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.core.properties.MessageProperties;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 异常唯一发布入口：结构化 payload + 本地日志 + 异步 EXCEPTION 事件。
 */
public final class ExceptionReporter {

    private static final Logger log = LoggerFactory.getLogger(ExceptionReporter.class);

    private ExceptionReporter() {
    }

    public static void reportBusiness(ExceptionCodeEnum code, String message, String scene, Packet packet) {
        report(ExceptionEventPayload.business(code, message, scene, packet), null);
    }

    public static void reportSystem(ExceptionCodeEnum code, String message, String scene, Packet packet) {
        report(ExceptionEventPayload.system(code, message, scene, packet, null), null);
    }

    public static void reportSystem(ExceptionCodeEnum code, String message, String scene,
                                    Packet packet, Throwable cause) {
        report(ExceptionEventPayload.system(code, message, scene, packet, cause), cause);
    }

    public static void reportPipeline(String scene, Throwable cause) {
        report(ExceptionEventPayload.pipeline(scene, cause), cause);
    }

    public static void report(ExceptionEventPayload payload) {
        report(payload, null);
    }

    public static void report(ExceptionEventPayload payload, Throwable cause) {
        if (payload == null) {
            return;
        }
        ExceptionEventPayload normalized = truncate(payload);
        logLocal(normalized, cause);
        if (normalized.severity() == ExceptionSeverity.BUSINESS) {
            return;
        }
        MessageContext.publishEvent(new MessageEvent(normalized, MessageEventTypeEnum.EXCEPTION), true);
    }

    public static ExceptionRecord toRecord(ExceptionEventPayload payload, long publishTime) {
        ExceptionEventPayload p = truncate(payload);
        ExceptionRecord record = new ExceptionRecord();
        record.setSeverity(p.severity());
        record.setCode(p.code());
        record.setCodeValue(p.code() != null ? p.code().getCode() : null);
        record.setMessage(p.message());
        record.setScene(p.scene());
        record.setPacketId(p.packetId());
        record.setMessageType(p.messageType());
        record.setAppKey(p.appKey());
        record.setFrom(p.from());
        record.setTo(p.to());
        record.setCauseType(p.causeType());
        record.setNodeId(resolveNodeId());
        record.setPublishTime(publishTime);
        return record;
    }

    private static ExceptionEventPayload truncate(ExceptionEventPayload payload) {
        int max = resolveMaxLength();
        String message = truncateText(payload.message(), max);
        if (StringUtils.equals(message, payload.message())) {
            return payload;
        }
        return new ExceptionEventPayload(
                payload.severity(),
                payload.code(),
                message,
                payload.scene(),
                payload.packetId(),
                payload.messageType(),
                payload.appKey(),
                payload.from(),
                payload.to(),
                payload.causeType()
        );
    }

    private static void logLocal(ExceptionEventPayload payload, Throwable cause) {
        ExceptionSeverity severity = payload.severity() != null ? payload.severity() : ExceptionSeverity.SYSTEM;
        if (severity == ExceptionSeverity.BUSINESS) {
            if (cause != null) {
                log.warn("业务拒绝 scene={}, code={}, packetId={}, msg={}",
                        payload.scene(), payload.code(), payload.packetId(), payload.message(), cause);
            } else {
                log.warn("业务拒绝 scene={}, code={}, packetId={}, msg={}",
                        payload.scene(), payload.code(), payload.packetId(), payload.message());
            }
            return;
        }
        if (cause != null) {
            log.error("异常上报 scene={}, severity={}, code={}, packetId={}, msg={}",
                    payload.scene(), severity, payload.code(), payload.packetId(), payload.message(), cause);
        } else {
            log.error("异常上报 scene={}, severity={}, code={}, packetId={}, msg={}",
                    payload.scene(), severity, payload.code(), payload.packetId(), payload.message());
        }
    }

    private static String truncateText(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    private static int resolveMaxLength() {
        MessageProperties properties = MessageContext.messageProperties;
        return properties != null ? properties.getExceptionMessageMaxLength() : 512;
    }

    private static String resolveNodeId() {
        MessageProperties properties = MessageContext.messageProperties;
        if (properties == null) {
            return null;
        }
        try {
            return properties.getLocalServerAddress();
        } catch (Exception ignored) {
            return properties.getApplicationName();
        }
    }
}
