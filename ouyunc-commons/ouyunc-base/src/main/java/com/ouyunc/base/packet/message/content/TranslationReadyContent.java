package com.ouyunc.base.packet.message.content;

import java.io.Serial;
import java.io.Serializable;

/**
 * 译文就绪协议体。实时 overlay 的 content（contentType=-112）；历史走 HTTP 并列字段，不写 Message.extra。
 * 客服 ticketId 写 {@code Message.correlationId}，不进本对象。
 */
public class TranslationReadyContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 原消息 packetId */
    private Long packetId;
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
            Long packetId,
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

    public Long getPacketId() {
        return packetId;
    }

    public void setPacketId(Long packetId) {
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
