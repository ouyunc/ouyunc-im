package com.ouyunc.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * IM会话消息偏移量实体类
 * 对应MongoDB中的文档集合
 */
@TableName("ouyunc_im_session_message_offset")
@Document(collection = "ouyunc_im_session_message_offset")
// 创建复合索引，对应MySQL中的复合主键，确保唯一性
@CompoundIndexes({
        @CompoundIndex(name = "from_to_type_idx", def = "{'from': 1, 'to': 1, 'type': 1}", unique = true)
})
public class SessionMessageOffsetEntity {

    private Long from; // 发送方ID（雪花ID）

    @Indexed(name = "idx_to") // 对应MySQL中的to字段索引
    private Long to; // 接收方ID（用户或群组，雪花ID）

    private Integer type; // 会话类型：1-一对一，2-群聊

    @Field("session_message_offset")
    private Long sessionMessageOffset = 0L; // 会话消息偏移量，默认值0

    // 构造方法
    public SessionMessageOffsetEntity() {}

    public SessionMessageOffsetEntity(Long from, Long to, Integer type, Long sessionMessageOffset) {
        this.from = from;
        this.to = to;
        this.type = type;
        this.sessionMessageOffset = sessionMessageOffset;
    }

    public static final class Fields {
        public static final String from = "from";
        public static final String to = "to";
        public static final String type = "type";
        public static final String sessionMessageOffset = "session_message_offset";
    }


    public Long getFrom() {
        return from;
    }

    public void setFrom(Long from) {
        this.from = from;
    }

    public Long getTo() {
        return to;
    }

    public void setTo(Long to) {
        this.to = to;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Long getSessionMessageOffset() {
        return sessionMessageOffset;
    }

    public void setSessionMessageOffset(Long sessionMessageOffset) {
        this.sessionMessageOffset = sessionMessageOffset;
    }
}
