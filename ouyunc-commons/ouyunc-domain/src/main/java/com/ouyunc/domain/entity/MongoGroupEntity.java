package com.ouyunc.domain.entity;


import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
* 群信息表
* @TableName ouyunc_im_group
*/
public class MongoGroupEntity extends GroupEntity {
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

    public MongoGroupEntity() {
    }

    public MongoGroupEntity(String id, String groupCode, String groupName, String groupAvatar, String groupDescription, String groupAnnouncement, Integer groupJoinPolicy, Integer status, Integer silence, String appKey, LocalDateTime createTime, LocalDateTime updateTime, Long delFlag, LocalDateTime expireAt) {
        super(id, groupCode, groupName, groupAvatar, groupDescription, groupAnnouncement, groupJoinPolicy, status, silence, appKey, createTime, updateTime, delFlag);
        this.expireAt = expireAt;
    }
}

