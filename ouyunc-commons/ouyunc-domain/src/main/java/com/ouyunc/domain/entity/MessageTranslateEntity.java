package com.ouyunc.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息译文快照。id = 原消息 packetId；联合主键 (id, language)。
 * 机器/LLM 仅首次插入；人工修正才更新。
 */
@TableName("ouyunc_im_message_translate")
public class MessageTranslateEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 原消息 packetId，对应 ouyunc_im_message.id */
    @Field("id")
    private Long id;

    /** 目标语种（规范化） */
    @Field("language")
    private String language;

    @Field("source_language")
    private String sourceLanguage;

    @Field("app_key")
    private String appKey;

    @Field("translate_content")
    private String translateContent;

    /** {@link com.ouyunc.base.constant.enums.MessageTranslateProviderEnum} */
    @Field("provider")
    private Byte provider;

    @Field("create_time")
    private LocalDateTime createTime;

    @Field("update_time")
    private LocalDateTime updateTime;

    public MessageTranslateEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public void setSourceLanguage(String sourceLanguage) {
        this.sourceLanguage = sourceLanguage;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public static final class Fields {
        public static final String id = "id";
        public static final String language = "language";
        public static final String sourceLanguage = "source_language";
        public static final String appKey = "app_key";
        public static final String translateContent = "translate_content";
        public static final String provider = "provider";
        public static final String createTime = "create_time";
        public static final String updateTime = "update_time";
        public static final String expireAt = "expire_at";
    }
}
