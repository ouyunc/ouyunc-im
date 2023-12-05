package com.ouyunc.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

/**
 * 群成员信息
 */
@TableName("ouyunc_im_group_user")
public class ImGroupUser implements Serializable {
    private static final long serialVersionUID = 203;


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
     * 群组id
     */
    private Long groupId;

    /**
     * 群组别名（每个群成员显示的群的名字）
     */
    private String groupNickName;

    /**
     * 群成员id
     */
    private Long userId;


    /**
     * 用户昵称（用户在群里的昵称）
     */
    private String userNickName;

    /**
     * 是否是群主，0-否，1-是
     */
    private Integer isLeader;

    /**
     * 是否是群管理员，0-否，1-是
     */
    private Integer isManager;

    /**
     * 是否屏蔽群，0-未屏蔽，1-屏蔽
     */
    private Integer isShield;

    /**
     * 用户在群中的状态，0-正常，1-被禁言
     */
    private Integer mushin;


    /**
     * 创建时间
     */
    private String createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserNickName() {
        return userNickName;
    }

    public String getGroupNickName() {
        return groupNickName;
    }

    public void setGroupNickName(String groupNickName) {
        this.groupNickName = groupNickName;
    }

    public void setUserNickName(String userNickName) {
        this.userNickName = userNickName;
    }

    public Integer getIsLeader() {
        return isLeader;
    }

    public void setIsLeader(Integer isLeader) {
        this.isLeader = isLeader;
    }

    public Integer getIsManager() {
        return isManager;
    }

    public void setIsManager(Integer isManager) {
        this.isManager = isManager;
    }

    public Integer getIsShield() {
        return isShield;
    }

    public void setIsShield(Integer isShield) {
        this.isShield = isShield;
    }

    public Integer getMushin() {
        return mushin;
    }

    public void setMushin(Integer mushin) {
        this.mushin = mushin;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public ImGroupUser() {
    }

    public ImGroupUser(Long id, Long groupId, String groupNickName, Long userId, String userNickName, Integer isLeader, Integer isManager, Integer isShield, Integer mushin, String createTime) {
        this.id = id;
        this.groupId = groupId;
        this.groupNickName = groupNickName;
        this.userId = userId;
        this.userNickName = userNickName;
        this.isLeader = isLeader;
        this.isManager = isManager;
        this.isShield = isShield;
        this.mushin = mushin;
        this.createTime = createTime;
    }

    public ImGroupUser(Long id, String appKey, Long groupId, String groupNickName, Long userId, String userNickName, Integer isLeader, Integer isManager, Integer isShield, Integer mushin, String createTime) {
        this.id = id;
        this.appKey = appKey;
        this.groupId = groupId;
        this.groupNickName = groupNickName;
        this.userId = userId;
        this.userNickName = userNickName;
        this.isLeader = isLeader;
        this.isManager = isManager;
        this.isShield = isShield;
        this.mushin = mushin;
        this.createTime = createTime;
    }
}
