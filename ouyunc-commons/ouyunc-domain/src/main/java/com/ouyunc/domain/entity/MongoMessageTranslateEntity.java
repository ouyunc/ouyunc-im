package com.ouyunc.domain.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * Mongo 热译文，TTL 与消息 Mongo 一致（约 3 个月）。
 */
@Document(collection = "ouyunc_im_message_translate")
@CompoundIndexes({
        @CompoundIndex(name = "uk_id_language", def = "{'id': 1, 'language': 1}", unique = true)
})
public class MongoMessageTranslateEntity extends MessageTranslateEntity {

    /**
     * Mongo _id，避免父类 packetId 字段名 id 被当成主键导致同一消息只能存一种语种。
     */
    @Id
    private String documentId;

    @Field("expire_at")
    @Indexed(expireAfter = "0s")
    private LocalDateTime expireAt;

    public MongoMessageTranslateEntity() {
    }

    public static String documentId(Long packetId, String language) {
        return packetId + ":" + language;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public static MongoMessageTranslateEntity from(MessageTranslateEntity src, LocalDateTime expireAt) {
        MongoMessageTranslateEntity target = new MongoMessageTranslateEntity();
        target.setDocumentId(documentId(src.getId(), src.getLanguage()));
        target.setId(src.getId());
        target.setLanguage(src.getLanguage());
        target.setSourceLanguage(src.getSourceLanguage());
        target.setAppKey(src.getAppKey());
        target.setTranslateContent(src.getTranslateContent());
        target.setProvider(src.getProvider());
        target.setCreateTime(src.getCreateTime());
        target.setUpdateTime(src.getUpdateTime());
        target.setExpireAt(expireAt);
        return target;
    }
}
