package com.ouyunc.domain.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
* 好友请求
* @TableName ouyunc_im_friend_request
*/
@Document(collection = "ouyunc_im_friend_request")
public class MongoFriendRequestEntity implements Serializable {

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
    * 好友请求会话状态：0-待处理， 1-已同意，2-已拒绝，3-已过期
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



    public static final class Fields {
        public static final String id = "id";
        public static final String from = "from";
        public static final String to = "to";

    }
    public MongoFriendRequestEntity() {
    }

    public MongoFriendRequestEntity(Long id, String from, String to, Long sessionBeginTime, Long sessionEndTime, Integer status, LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.sessionBeginTime = sessionBeginTime;
        this.sessionEndTime = sessionEndTime;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
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
