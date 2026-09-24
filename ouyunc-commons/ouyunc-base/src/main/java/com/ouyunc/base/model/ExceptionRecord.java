package com.ouyunc.base.model;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.ExceptionSeverity;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import org.apache.commons.lang3.StringUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * 异常落盘/MQ 合约（精简字段，不含堆栈、不含完整 Packet）。
 */
public class ExceptionRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private ExceptionSeverity severity;
    private ExceptionCodeEnum code;
    private Integer codeValue;
    private String message;
    private String scene;
    private Long packetId;
    private Byte messageType;
    private String appKey;
    private String from;
    private String to;
    private String causeType;
    private String nodeId;
    private long publishTime;

    public ExceptionRecord() {
    }

    public static ExceptionRecord of(ExceptionSeverity severity, ExceptionCodeEnum code,
                                     String message, String scene) {
        return of(severity, code, message, scene, null, null);
    }

    public static ExceptionRecord of(ExceptionSeverity severity, ExceptionCodeEnum code,
                                     String message, String scene, Packet packet) {
        return of(severity, code, message, scene, packet, null);
    }

    public static ExceptionRecord of(ExceptionSeverity severity, ExceptionCodeEnum code,
                                     String message, String scene, Packet packet, String causeType) {
        ExceptionCodeEnum resolved = code != null ? code : ExceptionCodeEnum.UNKNOWN_ERROR;
        ExceptionRecord record = new ExceptionRecord();
        record.setSeverity(severity != null ? severity : ExceptionSeverity.SYSTEM);
        record.setCode(resolved);
        record.setCodeValue(resolved.getCode());
        record.setMessage(StringUtils.defaultIfBlank(message, resolved.getMessage()));
        record.setScene(StringUtils.defaultIfBlank(scene, "unknown"));
        record.setCauseType(causeType);
        record.setPublishTime(System.currentTimeMillis());
        if (packet != null) {
            if (packet.getPacketId() > 0) {
                record.setPacketId(packet.getPacketId());
            }
            record.setMessageType(packet.getMessageType());
            Message msg = packet.getMessage();
            if (msg != null) {
                record.setFrom(msg.getFrom());
                record.setTo(msg.getTo());
                Metadata metadata = msg.getMetadataOrNull();
                if (metadata != null) {
                    record.setAppKey(metadata.getIngress().getAppKey());
                }
            }
        }
        return record;
    }

    public ExceptionSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(ExceptionSeverity severity) {
        this.severity = severity;
    }

    public ExceptionCodeEnum getCode() {
        return code;
    }

    public void setCode(ExceptionCodeEnum code) {
        this.code = code;
    }

    public Integer getCodeValue() {
        return codeValue;
    }

    public void setCodeValue(Integer codeValue) {
        this.codeValue = codeValue;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public Long getPacketId() {
        return packetId;
    }

    public void setPacketId(Long packetId) {
        this.packetId = packetId;
    }

    public Byte getMessageType() {
        return messageType;
    }

    public void setMessageType(Byte messageType) {
        this.messageType = messageType;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getCauseType() {
        return causeType;
    }

    public void setCauseType(String causeType) {
        this.causeType = causeType;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public long getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(long publishTime) {
        this.publishTime = publishTime;
    }
}
