package com.ouyunc.domain.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
* 好友请求会话
* @TableName ouyunc_im_friend_request_session
*/
@Document(collection = "ouyunc_im_friend_request_session")
@CompoundIndexes({
        @CompoundIndex(name = "from_to_idx", def = "{'from': 1, 'to': 1}")
})
public class MongoFriendRequestSessionEntity implements Serializable {

    /**
    * 主键id
    */
    @Id
    private Long id;

    /**
    * 发送方，唯一标识
    */
    @Field("from")
    private String from;

    /**
    * 接收方唯一标识
    */
    @Field("to")
    private String to;

    /**
    * 会话开始时间戳，毫秒
    */
    @Field("session_begin_time")
    private Long sessionBeginTime;

    /**
     * 会话结束时间戳，毫秒
     */
    @Field("session_end_time")
    private Long sessionEndTime;

    /**
    * 好友请求会话状态：0-待处理， 1-已同意，2-已拒绝，3-已过期，4-已失效 (如果已经是好友，另一个就是重置时效状态)，5-自动同意
    */
    @Field("status")
    private Integer status;

    /**
    * 创建时间
    */
    @Field("create_time")
    private LocalDateTime createTime;

    /**
    * 修改时间
    */
    @Field("update_time")
    private LocalDateTime updateTime;



    /**
     * 过期时间
     */
    @Field("expire_at")
    private LocalDateTime expireAt;

    public static final class Fields {
        public static final String id = "id";
        public static final String from = "from";
        public static final String to = "to";
        public static final String status = "status";
        public static final String expireAt = "expire_at";
    }
    public MongoFriendRequestSessionEntity() {
    }

    public MongoFriendRequestSessionEntity(Long id, String from, String to, Long sessionBeginTime, Long sessionEndTime, Integer status, LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.sessionBeginTime = sessionBeginTime;
        this.sessionEndTime = sessionEndTime;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    public MongoFriendRequestSessionEntity(Long id, String from, String to, Long sessionBeginTime, Long sessionEndTime, Integer status, LocalDateTime createTime, LocalDateTime updateTime, LocalDateTime expireAt) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.sessionBeginTime = sessionBeginTime;
        this.sessionEndTime = sessionEndTime;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.expireAt = expireAt;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getSessionBeginTime() {
        return sessionBeginTime;
    }

    public void setSessionBeginTime(Long sessionBeginTime) {
        this.sessionBeginTime = sessionBeginTime;
    }

    public Long getSessionEndTime() {
        return sessionEndTime;
    }

    public void setSessionEndTime(Long sessionEndTime) {
        this.sessionEndTime = sessionEndTime;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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
}
