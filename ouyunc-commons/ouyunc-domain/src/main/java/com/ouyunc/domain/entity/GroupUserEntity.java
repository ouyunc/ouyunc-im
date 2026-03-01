package com.ouyunc.domain.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
* 群成员表
* @TableName ouyunc_im_group_user
*/
@Document(collection = "ouyunc_im_group_user")
@CompoundIndexes({
        @CompoundIndex(name = "user_group_idx", def = "{'user_id': 1, 'group_id': 1}", unique = true)
})
@TableName("ouyunc_im_group_user")
public class GroupUserEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
    * 主键id
    */
    @Id
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 群组id
     */
    @Field("group_id")
    private String groupId;

    /**
     * 群组code
     */
    @Indexed
    @Field("group_code")
    private String groupCode;

    /**
     * 群组别名（该用户对这个群起的别名）
     */
    @Field("group_nick_name")
    private String groupNickName;

    /**
     * 用户id
     */
    @Field("user_id")
    private String userId;


    /**
     * 用户code
     */
    @Indexed
    @Field("user_code")
    private String userCode;



    /**
     * 职位，0-普通成员，1-管理员，2-群主
     */
    private Integer post;


    /**
     * 用户昵称（用户在群里的昵称）
     */
    @Field("user_nick_name")
    private String userNickName;

    /**
     * 是否屏蔽群（不会接收到群的信息），0-未屏蔽，1-屏蔽
     */
    private Integer shield;

    /**
     * 用户在群中的状态，0-未被禁言，1-被禁言
     */
    private Integer silence;


    /**
     * 会话session 加群方式：1-主动加群，2-被动加群（被邀请），3-扫码加群  ......
     */
    private Integer way;


    /**
     * 会话渠道，从哪里加入的，预留
     */
    private Integer channel;

    /**
     * 成为好友的时间戳， 毫秒
     */
    @Field("join_time")
    private Long joinTime;


    /**
     * 创建时间
     */
    @Field("create_time")
    private LocalDateTime createTime;



    public static final class Fields {
        public static final String id = "id";
        public static final String groupId = "group_id";
        public static final String groupCode = "group_code";
        public static final String userId = "user_id";
        public static final String userIds = "userIds";
        public static final String userCode = "user_code";
    }

    public GroupUserEntity() {
    }

    public GroupUserEntity(Long id, String groupId, String groupCode,  String groupNickName, String userId, String userCode, Integer post, String userNickName, Integer shield, Integer silence,  Long joinTime, LocalDateTime createTime) {
        this.id = id;
        this.groupId = groupId;
        this.groupCode = groupCode;
        this.groupNickName = groupNickName;
        this.userId = userId;
        this.userCode = userCode;
        this.post = post;
        this.userNickName = userNickName;
        this.shield = shield;
        this.silence = silence;
        this.joinTime = joinTime;
        this.createTime = createTime;
    }

    public GroupUserEntity(Long id, String groupId, String groupCode,  String groupNickName, String userId, String userCode,  Integer post, String userNickName, Integer shield, Integer silence, Integer way, Integer channel,  Long joinTime, LocalDateTime createTime) {
        this.id = id;
        this.groupId = groupId;
        this.groupCode = groupCode;
        this.groupNickName = groupNickName;
        this.userId = userId;
        this.userCode = userCode;
        this.post = post;
        this.userNickName = userNickName;
        this.shield = shield;
        this.silence = silence;
        this.way = way;
        this.channel = channel;
        this.joinTime = joinTime;
        this.createTime = createTime;
    }

    public GroupUserEntity(Long id, String groupId, String groupCode, String groupNickName, String userId, String userCode, Integer post, String userNickName, Integer shield, Integer silence, Integer way, Integer channel, Long joinTime) {
        this.id = id;
        this.groupId = groupId;
        this.groupCode = groupCode;
        this.groupNickName = groupNickName;
        this.userId = userId;
        this.userCode = userCode;
        this.post = post;
        this.userNickName = userNickName;
        this.shield = shield;
        this.silence = silence;
        this.way = way;
        this.channel = channel;
        this.joinTime = joinTime;
    }




    public Integer getWay() {
        return way;
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

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Integer getPost() {
        return post;
    }

    public void setPost(Integer post) {
        this.post = post;
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

    public Integer getSilence() {
        return silence;
    }

    public void setSilence(Integer silence) {
        this.silence = silence;
    }


    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getUserCode() {
        return userCode;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
    }
}
