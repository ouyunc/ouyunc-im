package com.ouyunc.domain.entity;


import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
* 群成员表
* @TableName ouyunc_im_group_user
*/
public class MongoGroupUserEntity extends GroupUserEntity {
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

    public MongoGroupUserEntity() {
    }

    public MongoGroupUserEntity(Long id, String groupId, String groupCode, String groupNickName, String userId, String userCode, Integer post, String userNickName, Integer shield, Integer silence, Integer way, Integer channel, Long joinTime, LocalDateTime expireAt) {
        super(id, groupId, groupCode, groupNickName, userId, userCode, post, userNickName, shield, silence, way, channel, joinTime);
        this.expireAt = expireAt;
    }

    public MongoGroupUserEntity(Long id, String groupId, String groupCode, String groupNickName, String userId, String userCode, Integer post, String userNickName, Integer shield, Integer silence, Integer way, Integer channel, Long joinTime, LocalDateTime createTime, LocalDateTime expireAt) {
        super(id, groupId, groupCode, groupNickName, userId, userCode, post, userNickName, shield, silence, way, channel, joinTime, createTime);
        this.expireAt = expireAt;
    }

    public MongoGroupUserEntity(Long id, String groupId, String groupCode, String groupNickName, String userId, String userCode, Integer post, String userNickName, Integer shield, Integer silence, Long joinTime, LocalDateTime createTime, LocalDateTime expireAt) {
        super(id, groupId, groupCode, groupNickName, userId, userCode, post, userNickName, shield, silence, joinTime, createTime);
        this.expireAt = expireAt;
    }
}
