package com.ouyunc.domain.entity;


import java.io.Serializable;
import java.time.LocalDateTime;

/**
* 群信息表
* @TableName ouyunc_im_group
*/
public class GroupEntity implements Serializable {

    /**
    * 主键id
    */
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

    /**
    * 主键id
    */
    private void setId(Long id){
    this.id = id;
    }

    /**
    * 群组名称
    */
    private void setGroupName(String groupName){
    this.groupName = groupName;
    }

    /**
    * 群组头像
    */
    private void setGroupAvatar(String groupAvatar){
    this.groupAvatar = groupAvatar;
    }

    /**
    * 群组描述
    */
    private void setGroupDescription(String groupDescription){
    this.groupDescription = groupDescription;
    }

    /**
    * 群组公告
    */
    private void setGroupAnnouncement(String groupAnnouncement){
    this.groupAnnouncement = groupAnnouncement;
    }

    /**
    * 群加入策略：0-加群需要验证，1-加群自动同意
    */
    private void setGroupJoinPolicy(Integer groupJoinPolicy){
    this.groupJoinPolicy = groupJoinPolicy;
    }

    /**
    * 群状态，1-正常，2-异常（被平台封禁）
    */
    private void setStatus(Integer status){
    this.status = status;
    }

    /**
    * 是否全体禁言（群主和管理员除外），0-不禁言，1-禁言
    */
    private void setMushin(Integer mushin){
    this.mushin = mushin;
    }

    /**
    * 应用appKey
    */
    private void setAppKey(String appKey){
    this.appKey = appKey;
    }


    /**
    * 是否删除：1-已删除，0-未删除
    */
    private void setDeleted(Integer deleted){
    this.deleted = deleted;
    }


    /**
    * 主键id
    */
    private Long getId(){
    return this.id;
    }

    /**
    * 群组名称
    */
    private String getGroupName(){
    return this.groupName;
    }

    /**
    * 群组头像
    */
    private String getGroupAvatar(){
    return this.groupAvatar;
    }

    /**
    * 群组描述
    */
    private String getGroupDescription(){
    return this.groupDescription;
    }

    /**
    * 群组公告
    */
    private String getGroupAnnouncement(){
    return this.groupAnnouncement;
    }

    /**
    * 群加入策略：0-加群需要验证，1-加群自动同意
    */
    private Integer getGroupJoinPolicy(){
    return this.groupJoinPolicy;
    }

    /**
    * 群状态，1-正常，2-异常（被平台封禁）
    */
    private Integer getStatus(){
    return this.status;
    }

    /**
    * 是否全体禁言（群主和管理员除外），0-不禁言，1-禁言
    */
    private Integer getMushin(){
    return this.mushin;
    }

    /**
    * 应用appKey
    */
    private String getAppKey(){
    return this.appKey;
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

    /**
    * 是否删除：1-已删除，0-未删除
    */
    private Integer getDeleted(){
    return this.deleted;
    }

}
