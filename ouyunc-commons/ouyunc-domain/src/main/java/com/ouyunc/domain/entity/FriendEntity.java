package com.ouyunc.domain.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
* 好友表
* @TableName ouyunc_im_friend
*/
@TableName("ouyunc_im_friend")
public class FriendEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
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
    * 好友用户code
    */
    private String friendUserCode;

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
     * 会话session 加好友方式：1-主动加好友，2-扫码加好友  ......
     */
    private Integer way;


    /**
     * 会话渠道，从哪里加入的，预留
     */
    private Integer channel;

    /**
     * 成为好友的时间戳， 毫秒
     */
    private Long joinTime;


    /**
    * 创建时间
    */
    private LocalDateTime createTime;

    /**
    * 修改时间
    */
    private LocalDateTime updateTime;


    public static final class Fields {
        public static final String id = "id";
        public static final String userId = "user_id";
        public static final String friendUserCode = "friend_user_code";
        public static final String friendUserId = "friend_user_id";
        public static final String shield = "shield";
        public static final String way = "way";
        public static final String channel = "channel";
        public static final String joinTime = "join_time";
        public static final String createTime = "create_time";
        public static final String updateTime = "update_time";
    }
    public FriendEntity() {
    }

    public FriendEntity(Long id, Long userId, Long friendUserId, String friendUserCode, String friendNickName, Integer shield,  Long joinTime, LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.userId = userId;
        this.friendUserId = friendUserId;
        this.friendUserCode = friendUserCode;
        this.friendNickName = friendNickName;
        this.shield = shield;
        this.joinTime = joinTime;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    public FriendEntity(Long id, Long userId, Long friendUserId, String friendUserCode, String friendNickName, Integer shield, Integer way, Integer channel, Long joinTime, LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.userId = userId;
        this.friendUserId = friendUserId;
        this.friendUserCode = friendUserCode;
        this.friendNickName = friendNickName;
        this.shield = shield;
        this.way = way;
        this.channel = channel;
        this.joinTime = joinTime;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }


    public Integer getWay() {
        return way;
    }

    public String getFriendUserCode() {
        return friendUserCode;
    }

    public void setFriendUserCode(String friendUserCode) {
        this.friendUserCode = friendUserCode;
    }

    public void setWay(Integer way) {
        this.way = way;
    }

    public Integer getChannel() {
        return channel;
    }

    public void setChannel(Integer channel) {
        this.channel = channel;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        FriendEntity that = (FriendEntity) o;
        return Objects.equals(userId, that.userId) && Objects.equals(friendUserId, that.friendUserId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, friendUserId);
    }
}
