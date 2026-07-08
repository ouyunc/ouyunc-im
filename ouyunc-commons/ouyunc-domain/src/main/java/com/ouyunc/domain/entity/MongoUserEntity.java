package com.ouyunc.domain.entity;


import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * 用户表
 *
 * @TableName ouyunc_im_user
 */
public class MongoUserEntity extends UserEntity {

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

    public MongoUserEntity() {
    }

    public MongoUserEntity(String id, String openId, String code, String username, String password, String nickName, String avatar, String motto, Integer age, Integer sex, String email, String phoneNum, String idCardNo, Integer groupInvitePolicy, Integer friendJoinPolicy, Integer status, String appKey, Integer type, String externalId, String unionId, LocalDateTime createTime, LocalDateTime updateTime, Integer deleted, LocalDateTime expireAt) {
        super(id, openId, code, username, password, nickName, avatar, motto, age, sex, email, phoneNum, idCardNo, groupInvitePolicy, friendJoinPolicy, status, appKey, type, externalId, unionId, createTime, updateTime, deleted);
        this.expireAt = expireAt;
    }
}

