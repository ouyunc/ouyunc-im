package com.ouyunc.domain.entity;


import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.Objects;

/**
* 好友表
* @TableName ouyunc_im_friend
*/
public class MongoFriendEntity extends FriendEntity {
    /**
     * 过期时间
     */
    @Field("expire_at")
    @Indexed(expireAfter = "0s")
    private LocalDateTime expireAt;


    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MongoFriendEntity that = (MongoFriendEntity) o;
        return Objects.equals(getUserId(), that.getUserId()) && Objects.equals(getFriendUserId(), that.getFriendUserId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUserId(), getFriendUserId());
    }
}
