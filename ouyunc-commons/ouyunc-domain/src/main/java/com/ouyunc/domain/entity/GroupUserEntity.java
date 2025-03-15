package com.ouyunc.domain.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
* 群成员表
* @TableName ouyunc_im_group_user
*/
@TableName("ouyunc_im_group_user")
public class GroupUserEntity implements Serializable {

    /**
    * 主键id
    */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
    * 群组id
    */
    private Long groupId;

    /**
    * 群组别名（该用户对这个群起的别名）
    */
    private String groupNickName;

    /**
    * 用户id
    */
    private Long userId;

    /**
    * 是否是群主，0-否，1-是
    */
    private Integer leader;

    /**
    * 是否是群管理员，0-否，1-是
    */
    private Integer manager;

    /**
    * 用户昵称（用户在群里的昵称）
    */
    private String userNickName;

    /**
    * 是否屏蔽群（不会接收到群的信息），0-未屏蔽，1-屏蔽
    */
    private Integer shield;

    /**
    * 用户在群中的状态，0-未被禁言，1-被禁言
    */
    private Integer mushin;


    /**
     * 会话消息偏移量，会话消息的接收时间；假如本次读取到会话A点，则下次从A点之后开始读取
     */
    private Long sessionMessageOffset;

    /**
     * 成为好友的时间戳， 毫秒
     */
    private Long joinTime;


    /**
    * 创建时间
    */
    private LocalDateTime createTime;

    public GroupUserEntity() {
    }

    public GroupUserEntity(Long id, Long groupId, String groupNickName, Long userId, Integer leader, Integer manager, String userNickName, Integer shield, Integer mushin, Long sessionMessageOffset, Long joinTime, LocalDateTime createTime) {
        this.id = id;
        this.groupId = groupId;
        this.groupNickName = groupNickName;
        this.userId = userId;
        this.leader = leader;
        this.manager = manager;
        this.userNickName = userNickName;
        this.shield = shield;
        this.mushin = mushin;
        this.sessionMessageOffset = sessionMessageOffset;
        this.joinTime = joinTime;
        this.createTime = createTime;
    }

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

    public String getGroupNickName() {
        return groupNickName;
    }

    public void setGroupNickName(String groupNickName) {
        this.groupNickName = groupNickName;
    }

    public Long getJoinTime() {
        return joinTime;
    }

    public void setJoinTime(Long joinTime) {
        this.joinTime = joinTime;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getLeader() {
        return leader;
    }

    public void setLeader(Integer leader) {
        this.leader = leader;
    }

    public Integer getManager() {
        return manager;
    }

    public void setManager(Integer manager) {
        this.manager = manager;
    }

    public String getUserNickName() {
        return userNickName;
    }

    public void setUserNickName(String userNickName) {
        this.userNickName = userNickName;
    }

    public Integer getShield() {
        return shield;
    }

    public void setShield(Integer shield) {
        this.shield = shield;
    }

    public Integer getMushin() {
        return mushin;
    }

    public void setMushin(Integer mushin) {
        this.mushin = mushin;
    }

    public Long getSessionMessageOffset() {
        return sessionMessageOffset;
    }

    public void setSessionMessageOffset(Long sessionMessageOffset) {
        this.sessionMessageOffset = sessionMessageOffset;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
