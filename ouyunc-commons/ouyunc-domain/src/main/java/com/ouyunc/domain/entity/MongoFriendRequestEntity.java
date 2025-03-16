package com.ouyunc.domain.entity;

import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
* 好友请求
* @TableName ouyunc_im_friend_request
*/
public class MongoFriendRequestEntity extends FriendRequestEntity {

    /**
     * 过期时间
     */
    @Field("expire_at")
    private LocalDateTime expireAt;

    public MongoFriendRequestEntity() {
    }

    public MongoFriendRequestEntity(Long id, String from, String to, Long sessionBeginTime, Long sessionEndTime, Integer status, LocalDateTime createTime, LocalDateTime updateTime, LocalDateTime expireAt) {
        super(id, from, to, sessionBeginTime, sessionEndTime, status, createTime, updateTime);
        this.expireAt = expireAt;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }
}
