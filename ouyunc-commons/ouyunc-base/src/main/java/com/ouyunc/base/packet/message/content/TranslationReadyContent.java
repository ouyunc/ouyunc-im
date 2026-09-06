package com.ouyunc.base.packet.message.content;

import java.io.Serial;
import java.io.Serializable;

/**
 * 译文就绪协议体。实时 SERVER_NOTIFY + contentType=-112 的 content；历史走 HTTP 并列字段，不写 Message.extra。
 * 客服 ticketId 写 {@code Message.correlationId}，不进本对象。
 * {@code packetId} 与 {@link QosAckContent#ackId} 一样用十进制字符串，避免 JS 精度丢失。
 */
public class TranslationReadyContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 原消息 packetId（雪花十进制字符串） */
    private String packetId;
    /** 目标语种（规范化） */
    private String targetLanguage;
    /** 源语种 */
    private String sourceLanguage;
    /** 译文 */
    private String translateContent;
    /** {@link com.ouyunc.base.constant.enums.MessageTranslateProviderEnum#getType()} */
    private Byte provider;

    public TranslationReadyContent() {
    }

    public TranslationReadyContent(
            String packetId,
            String targetLanguage,
            String sourceLanguage,
            String translateContent,
            Byte provider) {
        this.packetId = packetId;
        this.targetLanguage = targetLanguage;
        this.sourceLanguage = sourceLanguage;
        this.translateContent = translateContent;
        this.provider = provider;
    }

    /** 热路径 Packet.packetId 为 long，对外协议转十进制字符串。 */
    public static TranslationReadyContent of(
            long packetId,
            String targetLanguage,
            String sourceLanguage,
            String translateContent,
            Byte provider) {
        return new TranslationReadyContent(
                packetId > 0L ? String.valueOf(packetId) : null,
                targetLanguage,
                sourceLanguage,
                translateContent,
                provider);
    }

    public String getPacketId() {
        return packetId;
    }

    public void setPacketId(String packetId) {
        this.packetId = packetId;
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public void setTargetLanguage(String targetLanguage) {
        this.targetLanguage = targetLanguage;
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public void setSourceLanguage(String sourceLanguage) {
        this.sourceLanguage = sourceLanguage;
    }

    public String getTranslateContent() {
        return translateContent;
    }

    public void setTranslateContent(String translateContent) {
        this.translateContent = translateContent;
    }

    public Byte getProvider() {
        return provider;
    }

    public void setProvider(Byte provider) {
        this.provider = provider;
    }
}
