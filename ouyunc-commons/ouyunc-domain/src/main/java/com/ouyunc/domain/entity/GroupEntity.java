package com.ouyunc.domain.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
* 群信息表
* @TableName ouyunc_im_group
*/
@TableName("ouyunc_im_group")
public class GroupEntity implements Serializable {

    /**
    * 主键id
    */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
    * 群组名称
    */
    private String groupName;

    /**
    * 群组头像
    */
    private String groupAvatar;

    /**
    * 群组描述
    */
    private String groupDescription;

    /**
    * 群组公告
    */
    private String groupAnnouncement;

    /**
    * 群加入策略：0-加群需要验证，1-加群自动同意
    */
    private Integer groupJoinPolicy;

    /**
    * 群状态，1-正常，2-异常（被平台封禁）
    */
    private Integer status;

    /**
    * 是否全体禁言（群主和管理员除外），0-不禁言，1-禁言
    */
    private Integer mushin;

    /**
    * 应用appKey
    */
    private String appKey;

    /**
    * 创建时间
    */
    private LocalDateTime createTime;

    /**
    * 修改时间
    */
    private LocalDateTime updateTime;

    /**
    * 是否删除：1-已删除，0-未删除
    */
    private Integer deleted;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getGroupAvatar() {
        return groupAvatar;
    }

    public void setGroupAvatar(String groupAvatar) {
        this.groupAvatar = groupAvatar;
    }

    public String getGroupDescription() {
        return groupDescription;
    }

    public void setGroupDescription(String groupDescription) {
        this.groupDescription = groupDescription;
    }

    public String getGroupAnnouncement() {
        return groupAnnouncement;
    }

    public void setGroupAnnouncement(String groupAnnouncement) {
        this.groupAnnouncement = groupAnnouncement;
    }

    public Integer getGroupJoinPolicy() {
        return groupJoinPolicy;
    }

    public void setGroupJoinPolicy(Integer groupJoinPolicy) {
        this.groupJoinPolicy = groupJoinPolicy;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getMushin() {
        return mushin;
    }

    public void setMushin(Integer mushin) {
        this.mushin = mushin;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
