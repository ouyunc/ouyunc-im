package com.ouyunc.domain.entity;


import java.io.Serializable;
import java.time.LocalDateTime;

/**
* 好友表
* @TableName ouyunc_im_friend
*/
public class FriendEntity implements Serializable {

    /**
    * 主键id
    */
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
    private Integer isShield;

    /**
    * 创建时间
    */
    private LocalDateTime createTime;

    /**
    * 修改时间
    */
    private LocalDateTime updateTime;

    /**
    * 主键id
    */
    private void setId(Long id){
    this.id = id;
    }

    /**
    * 用户id
    */
    private void setUserId(Long userId){
    this.userId = userId;
    }

    /**
    * 好友用户id
    */
    private void setFriendUserId(Long friendUserId){
    this.friendUserId = friendUserId;
    }

    /**
    * 好友昵称
    */
    private void setFriendNickName(String friendNickName){
    this.friendNickName = friendNickName;
    }

    /**
    * 是否屏蔽该好友，0-未屏蔽，1-屏蔽
    */
    private void setIsShield(Integer isShield){
    this.isShield = isShield;
    }




    /**
    * 主键id
    */
    private Long getId(){
    return this.id;
    }

    /**
    * 用户id
    */
    private Long getUserId(){
    return this.userId;
    }

    /**
    * 好友用户id
    */
    private Long getFriendUserId(){
    return this.friendUserId;
    }

    /**
    * 好友昵称
    */
    private String getFriendNickName(){
    return this.friendNickName;
    }

    /**
    * 是否屏蔽该好友，0-未屏蔽，1-屏蔽
    */
    private Integer getIsShield(){
    return this.isShield;
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
