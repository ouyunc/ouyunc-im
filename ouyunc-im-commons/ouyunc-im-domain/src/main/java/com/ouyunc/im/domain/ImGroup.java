package com.ouyunc.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

/**
 * 群组信息
 */
@TableName("ouyunc_im_group")
public class ImGroup implements Serializable {
    private static final long serialVersionUID = 201;

    /**
     * 主键id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 所属平台唯一标识
     */
    private String appKey;

    /**
     * 群组名称
     */
    private String groupName;

    /**
     * 群组头像url
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
     * 是否全体禁言（群主和管理员除外），0-不禁言，1-禁言
     */
    private Integer mushin;

    /**
     * 群状态，0-正常，1-异常（被平台封禁）
     */
    private Integer status;


    /**
     * 创建时间
     */
    private String createTime;


    /**
     * 更新时间
     */
    private String updateTime;


    /**
     * 是否删除(0-否，1-是)
     */
    @TableLogic
    private byte deleted;


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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getMushin() {
        return mushin;
    }

    public void setMushin(Integer mushin) {
        this.mushin = mushin;
    }

    public Integer getGroupJoinPolicy() {
        return groupJoinPolicy;
    }

    public void setGroupJoinPolicy(Integer groupJoinPolicy) {
        this.groupJoinPolicy = groupJoinPolicy;
    }

    public byte getDeleted() {
        return deleted;
    }

    public void setDeleted(byte deleted) {
        this.deleted = deleted;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public ImGroup() {
    }

    public ImGroup(Long id, String appKey, String groupName, String groupAvatar, String groupDescription, String groupAnnouncement, Integer groupJoinPolicy, Integer mushin, Integer status, String createTime, String updateTime, byte deleted) {
        this.id = id;
        this.appKey = appKey;
        this.groupName = groupName;
        this.groupAvatar = groupAvatar;
        this.groupDescription = groupDescription;
        this.groupAnnouncement = groupAnnouncement;
        this.groupJoinPolicy = groupJoinPolicy;
        this.mushin = mushin;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.deleted = deleted;
    }
}
