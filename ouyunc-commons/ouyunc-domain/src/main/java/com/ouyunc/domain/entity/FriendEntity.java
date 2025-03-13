package com.ouyunc.domain.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
* 好友表
* @TableName ouyunc_im_friend
*/
@TableName("ouyunc_im_friend")
public class FriendEntity implements Serializable {

    /**
    * 主键id
    */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
    * 用户id
    */
    private Long userId;

    /**
    * 好友用户id
    */
    private Long friendUserId;

    /**
    * 好友昵称
    */
    private String friendNickName;

    /**
    * 是否屏蔽该好友，0-未屏蔽，1-屏蔽
    */
    private Integer shield;

    /**
     * 会话消息偏移量，会话消息的接收时间；假如本次读取到会话A点，则下次从A点之后开始读取
     */
    private Long sessionMessageOffset;

    /**
    * 创建时间
    */
    private LocalDateTime createTime;

    /**
    * 修改时间
    */
    private LocalDateTime updateTime;

    public FriendEntity() {
    }



    public FriendEntity(Long id, Long userId, Long friendUserId, String friendNickName, Integer shield, Long sessionMessageOffset, LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.userId = userId;
        this.friendUserId = friendUserId;
        this.friendNickName = friendNickName;
        this.shield = shield;
        this.sessionMessageOffset = sessionMessageOffset;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getFriendUserId() {
        return friendUserId;
    }

    public void setFriendUserId(Long friendUserId) {
        this.friendUserId = friendUserId;
    }

    public String getFriendNickName() {
        return friendNickName;
    }

    public void setFriendNickName(String friendNickName) {
        this.friendNickName = friendNickName;
    }

    public Integer getShield() {
        return shield;
    }

    public void setShield(Integer shield) {
        this.shield = shield;
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

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
