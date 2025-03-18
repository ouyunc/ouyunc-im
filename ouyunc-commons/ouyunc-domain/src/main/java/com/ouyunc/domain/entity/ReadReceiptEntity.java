package com.ouyunc.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
* 消息已读回执
* @TableName ouyunc_im_read_receipt
*/
@TableName("ouyunc_im_read_receipt")
@Document(collection = "ouyunc_im_read_receipt")
@CompoundIndexes({
        @CompoundIndex(name = "msgId_userId_idx", def = "{'msg_id': 1, 'user_id': 1}")
})
public class ReadReceiptEntity implements Serializable {

    /**
    * 主键
    */
    @Id
    @TableId(type = IdType.ASSIGN_ID)
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

    public static final class Fields {
        public static final String id = "id";
        public static final String msgId = "msg_id";
        public static final String userId = "user_id";
    }


    public ReadReceiptEntity() {
    }

    public ReadReceiptEntity(Long id, Long msgId, Long userId, Long readTime) {
        this.id = id;
        this.msgId = msgId;
        this.userId = userId;
        this.readTime = readTime;
    }

    public ReadReceiptEntity(Long id, Long msgId, Long userId, Long readTime, LocalDateTime createTime) {
        this.id = id;
        this.msgId = msgId;
        this.userId = userId;
        this.readTime = readTime;
        this.createTime = createTime;
    }

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
