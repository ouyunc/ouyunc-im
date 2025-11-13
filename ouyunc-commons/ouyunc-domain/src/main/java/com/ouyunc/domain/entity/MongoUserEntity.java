package com.ouyunc.domain.entity;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户表
 *
 * @TableName ouyunc_im_user
 */
@Document(collection = "ouyunc_im_user")
public class MongoUserEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 主键id
     */
    @Id
    private Long id;

    /**
     * 开放id
     */
    @Indexed
    @Field("open_id")
    private String openId;

    /**
     * 用户编号
     */
    @Indexed
    @Field("code")
    private String code;

    /**
     * 用户名称（对应于身份证）
     */
    @Field("username")
    private String username;

    /**
     * 用户名密码
     */
    @Field("password")
    private String password;

    /**
     * 用户别名
     */
    @Field("nick_name")
    private String nickName;

    /**
     * 用户头像url
     */
    @Field("avatar")
    private String avatar;

    /**
     * 座右铭/格言
     */
    @Field("motto")
    private String motto;

    /**
     * 年龄
     */
    @Field("age")
    private Integer age;

    /**
     * 性别：0-女，1-男
     */
    @Field("sex")
    private Integer sex;

    /**
     * 邮箱
     */
    @Indexed
    @Field("email")
    private String email;

    /**
     * 手机号（国内）
     */
    @Indexed
    @Field("phone_num")
    private String phoneNum;

    /**
     * 身份证号码
     */
    @Indexed
    @Field("id_card_no")
    private String idCardNo;

    /**
     * 群邀请的应答策略：0-需要验证，1-自动通过
     */
    @Field("group_invite_policy")
    private Integer groupInvitePolicy;

    /**
     * 好友添加的应答策略：0-需要验证，1-自动通过
     */
    @Field("friend_join_policy")
    private Integer friendJoinPolicy;

    /**
     * 用户状态：1-正常，2-异常（被平台封禁）
     */
    @Field("status")
    private Integer status;

    /**
     * 应用appKey
     */
    @Indexed
    @Field("app_key")
    private String appKey;

    /**
     * 是否是机器人：0-不是，1-是
     */
    @Field("robot")
    private Integer robot;

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
     * 是否删除，1-已删除，0-未删除
     */
    @Field("deleted")
    private Integer deleted;

    /**
     * 过期时间
     */
    @Field("expire_at")
    @Indexed(expireAfter = "0s")
    private LocalDateTime expireAt;


    public static final class Fields {
        public static final String id = "id";
        public static final String code = "code";
        public static final String openId = "open_id";
        public static final String appKey = "app_key";
        public static final String deleted = "deleted";
    }

    public MongoUserEntity() {
    }

    public MongoUserEntity(Long id, String openId, String code, String username, String password, String nickName, String avatar, String motto, Integer age, Integer sex, String email, String phoneNum, String idCardNo, Integer groupInvitePolicy, Integer friendJoinPolicy, Integer status, String appKey, Integer robot, LocalDateTime createTime, LocalDateTime updateTime, Integer deleted) {
        this.id = id;
        this.openId = openId;
        this.code = code;
        this.username = username;
        this.password = password;
        this.nickName = nickName;
        this.avatar = avatar;
        this.motto = motto;
        this.age = age;
        this.sex = sex;
        this.email = email;
        this.phoneNum = phoneNum;
        this.idCardNo = idCardNo;
        this.groupInvitePolicy = groupInvitePolicy;
        this.friendJoinPolicy = friendJoinPolicy;
        this.status = status;
        this.appKey = appKey;
        this.robot = robot;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.deleted = deleted;
    }

    public MongoUserEntity(Long id, String openId, String code, String username, String password, String nickName, String avatar, String motto, Integer age, Integer sex, String email, String phoneNum, String idCardNo, Integer groupInvitePolicy, Integer friendJoinPolicy, Integer status, String appKey, Integer robot, LocalDateTime createTime, LocalDateTime updateTime, Integer deleted, LocalDateTime expireAt) {
        this.id = id;
        this.openId = openId;
        this.code = code;
        this.username = username;
        this.password = password;
        this.nickName = nickName;
        this.avatar = avatar;
        this.motto = motto;
        this.age = age;
        this.sex = sex;
        this.email = email;
        this.phoneNum = phoneNum;
        this.idCardNo = idCardNo;
        this.groupInvitePolicy = groupInvitePolicy;
        this.friendJoinPolicy = friendJoinPolicy;
        this.status = status;
        this.appKey = appKey;
        this.robot = robot;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.deleted = deleted;
        this.expireAt = expireAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOpenId() {
        return openId;
    }

    public void setOpenId(String openId) {
        this.openId = openId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getMotto() {
        return motto;
    }

    public void setMotto(String motto) {
        this.motto = motto;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public Integer getSex() {
        return sex;
    }

    public void setSex(Integer sex) {
        this.sex = sex;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNum() {
        return phoneNum;
    }

    public void setPhoneNum(String phoneNum) {
        this.phoneNum = phoneNum;
    }

    public String getIdCardNo() {
        return idCardNo;
    }

    public void setIdCardNo(String idCardNo) {
        this.idCardNo = idCardNo;
    }

    public Integer getGroupInvitePolicy() {
        return groupInvitePolicy;
    }

    public void setGroupInvitePolicy(Integer groupInvitePolicy) {
        this.groupInvitePolicy = groupInvitePolicy;
    }

    public Integer getFriendJoinPolicy() {
        return friendJoinPolicy;
    }

    public void setFriendJoinPolicy(Integer friendJoinPolicy) {
        this.friendJoinPolicy = friendJoinPolicy;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public Integer getRobot() {
        return robot;
    }

    public void setRobot(Integer robot) {
        this.robot = robot;
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }
}

