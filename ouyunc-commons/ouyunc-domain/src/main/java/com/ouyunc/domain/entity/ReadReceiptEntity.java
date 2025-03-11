package com.ouyunc.domain.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
* 消息已读回执
* @TableName ouyunc_im_read_receipt
*/
@Document(collection = "ouyunc-im-read-receipt")
public class ReadReceiptEntity implements Serializable {

    /**
    * 主键
    */
    @Id
    private Long id;

    /**
    * 消息id,(packetId)
    */
    @Field("msg_id")
    private Long msgId;

    /**
    * 已读消息的用户id
    */
    @Field("user_id")
    private Long userId;

    /**
    * 已读时间戳
    */
    @Field("read_time")
    private Long readTime;

    /**
    * 创建时间,这个字段不需要存储到mongodb
    */
    @Transient
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMsgId() {
        return msgId;
    }

    public void setMsgId(Long msgId) {
        this.msgId = msgId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getReadTime() {
        return readTime;
    }

    public void setReadTime(Long readTime) {
        this.readTime = readTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
