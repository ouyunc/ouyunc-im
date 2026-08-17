package com.ouyunc.domain.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
* 群信息表
* @TableName ouyunc_im_group
*/
@Document(collection = "ouyunc_im_group")
@TableName("ouyunc_im_group")
public class GroupEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
    * 主键id
    */
    @Id
    @TableId(type = IdType.ASSIGN_ID)
    private String id;


    /**
     * 群组号码
     */
    @Indexed
    @Field("group_code")
    private String groupCode;

    /**
     * 群组名称
     */
    @Field("group_name")
    private String groupName;

    /**
     * 群组头像
     */
    @Field("group_avatar")
    private String groupAvatar;

    /**
     * 群组描述
     */
    @Field("group_description")
    private String groupDescription;

    /**
     * 群组公告
     */
    @Field("group_announcement")
    private String groupAnnouncement;

    /**
     * 群加入策略：0-加群需要验证，1-加群自动同意
     */
    @Field("group_join_policy")
    private Integer groupJoinPolicy;

    /**
     * 群状态，1-正常，2-异常（被平台封禁）
     */
    private Integer status;

    /**
     * 是否全体禁言（群主和管理员除外），0-不禁言，1-禁言
     */
    private Integer silence;

    /**
     * 应用appKey
     */
    @Indexed
    @Field("app_key")
    private String appKey;

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
     * 软删除：0-未删除；非0-已删除（毫秒时间戳）。MySQL / Mongo 统一键名 del_flag。
     */
    @Field("del_flag")
    @TableField("del_flag")
    private Long delFlag;

    public static final class Fields {
        public static final String id = "id";
        public static final String groupCode = "group_code";
        public static final String appKey = "app_key";
        /** Mongo / 查询字段名，对应文档键 del_flag */
        public static final String delFlag = "del_flag";
    }

    public GroupEntity() {
    }

    public GroupEntity(String id, String groupCode, String groupName, String groupAvatar, String groupDescription, String groupAnnouncement, Integer groupJoinPolicy, Integer status, Integer silence, String appKey, LocalDateTime createTime, LocalDateTime updateTime, Long delFlag) {
        this.id = id;
        this.groupCode = groupCode;
        this.groupName = groupName;
        this.groupAvatar = groupAvatar;
        this.groupDescription = groupDescription;
        this.groupAnnouncement = groupAnnouncement;
        this.groupJoinPolicy = groupJoinPolicy;
        this.status = status;
        this.silence = silence;
        this.appKey = appKey;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.delFlag = delFlag;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
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

    public Integer getSilence() {
        return silence;
    }

    public void setSilence(Integer silence) {
        this.silence = silence;
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

    public Long getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Long delFlag) {
        this.delFlag = delFlag;
    }
}
