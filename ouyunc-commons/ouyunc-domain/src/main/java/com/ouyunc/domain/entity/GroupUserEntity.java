package com.ouyunc.domain.entity;


import java.io.Serializable;
import java.time.LocalDateTime;

/**
* 群成员表
* @TableName ouyunc_im_group_user
*/
public class GroupUserEntity implements Serializable {

    /**
    * 主键id
    */
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
    private Integer isLeader;

    /**
    * 是否是群管理员，0-否，1-是
    */
    private Integer isManager;

    /**
    * 用户昵称（用户在群里的昵称）
    */
    private String userNickName;

    /**
    * 是否屏蔽群（不会接收到群的信息），0-未屏蔽，1-屏蔽
    */
    private Integer isShield;

    /**
    * 用户在群中的状态，0-未被禁言，1-被禁言
    */
    private Integer mushin;

    /**
    * 创建时间
    */
    private LocalDateTime createTime;

    /**
    * 主键id
    */
    private void setId(Long id){
    this.id = id;
    }

    /**
    * 群组id
    */
    private void setGroupId(Long groupId){
    this.groupId = groupId;
    }

    /**
    * 群组别名（该用户对这个群起的别名）
    */
    private void setGroupNickName(String groupNickName){
    this.groupNickName = groupNickName;
    }

    /**
    * 用户id
    */
    private void setUserId(Long userId){
    this.userId = userId;
    }

    /**
    * 是否是群主，0-否，1-是
    */
    private void setIsLeader(Integer isLeader){
    this.isLeader = isLeader;
    }

    /**
    * 是否是群管理员，0-否，1-是
    */
    private void setIsManager(Integer isManager){
    this.isManager = isManager;
    }

    /**
    * 用户昵称（用户在群里的昵称）
    */
    private void setUserNickName(String userNickName){
    this.userNickName = userNickName;
    }

    /**
    * 是否屏蔽群（不会接收到群的信息），0-未屏蔽，1-屏蔽
    */
    private void setIsShield(Integer isShield){
    this.isShield = isShield;
    }

    /**
    * 用户在群中的状态，0-未被禁言，1-被禁言
    */
    private void setMushin(Integer mushin){
    this.mushin = mushin;
    }




    /**
    * 主键id
    */
    private Long getId(){
    return this.id;
    }

    /**
    * 群组id
    */
    private Long getGroupId(){
    return this.groupId;
    }

    /**
    * 群组别名（该用户对这个群起的别名）
    */
    private String getGroupNickName(){
    return this.groupNickName;
    }

    /**
    * 用户id
    */
    private Long getUserId(){
    return this.userId;
    }

    /**
    * 是否是群主，0-否，1-是
    */
    private Integer getIsLeader(){
    return this.isLeader;
    }

    /**
    * 是否是群管理员，0-否，1-是
    */
    private Integer getIsManager(){
    return this.isManager;
    }

    /**
    * 用户昵称（用户在群里的昵称）
    */
    private String getUserNickName(){
    return this.userNickName;
    }

    /**
    * 是否屏蔽群（不会接收到群的信息），0-未屏蔽，1-屏蔽
    */
    private Integer getIsShield(){
    return this.isShield;
    }

    /**
    * 用户在群中的状态，0-未被禁言，1-被禁言
    */
    private Integer getMushin(){
    return this.mushin;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
