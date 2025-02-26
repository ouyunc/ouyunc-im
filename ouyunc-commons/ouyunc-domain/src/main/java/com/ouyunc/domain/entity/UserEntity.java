package com.ouyunc.domain.entity;


import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户表
 *
 * @TableName ouyunc_im_user
 */
public class UserEntity implements Serializable {

    /**
     * 主键id
     */
    private Long id;

    /**
     * 开放id
     */
    private String openId;

    /**
     * 用户名称（对应于身份证）
     */
    private String username;

    /**
     * 用户名密码
     */
    private String password;

    /**
     * 用户别名
     */
    private String nickName;

    /**
     * 用户头像url
     */
    private String avatar;

    /**
     * 座右铭/格言
     */
    private String motto;

    /**
     * 年龄
     */
    private Integer age;

    /**
     * 性别：0-女，1-男，2-其他
     */
    private Integer sex;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号（国内）
     */
    private String phoneNum;

    /**
     * 身份证号码
     */
    private String idCardNum;

    /**
     * 群邀请的应答策略：0-需要验证，1-自动通过
     */
    private Integer groupInvitePolicy;

    /**
     * 好友添加的应答策略：0-需要验证，1-自动通过
     */
    private Integer friendJoinPolicy;

    /**
     * 用户状态：1-正常，2-异常（被平台封禁）
     */
    private Integer status;

    /**
     * 应用appKey
     */
    private String appKey;

    /**
     * 是否是机器人：0-不是，1-是
     */
    private Integer robot;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 修改时间
     */
    private LocalDateTime updateTime;

    /**
     * 是否删除，1-已删除，0-未删除
     */
    private Integer deleted;

    /**
     * 主键id
     */
    private void setId(Long id) {
        this.id = id;
    }

    /**
     * 开放id
     */
    private void setOpenId(String openId) {
        this.openId = openId;
    }

    /**
     * 用户名称（对应于身份证）
     */
    private void setUsername(String username) {
        this.username = username;
    }

    /**
     * 用户名密码
     */
    private void setPassword(String password) {
        this.password = password;
    }

    /**
     * 用户别名
     */
    private void setNickName(String nickName) {
        this.nickName = nickName;
    }

    /**
     * 用户头像url
     */
    private void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    /**
     * 座右铭/格言
     */
    private void setMotto(String motto) {
        this.motto = motto;
    }

    /**
     * 年龄
     */
    private void setAge(Integer age) {
        this.age = age;
    }

    /**
     * 性别：0-女，1-男，2-其他
     */
    private void setSex(Integer sex) {
        this.sex = sex;
    }

    /**
     * 邮箱
     */
    private void setEmail(String email) {
        this.email = email;
    }

    /**
     * 手机号（国内）
     */
    private void setPhoneNum(String phoneNum) {
        this.phoneNum = phoneNum;
    }

    /**
     * 身份证号码
     */
    private void setIdCardNum(String idCardNum) {
        this.idCardNum = idCardNum;
    }

    /**
     * 群邀请的应答策略：0-需要验证，1-自动通过
     */
    private void setGroupInvitePolicy(Integer groupInvitePolicy) {
        this.groupInvitePolicy = groupInvitePolicy;
    }

    /**
     * 好友添加的应答策略：0-需要验证，1-自动通过
     */
    private void setFriendJoinPolicy(Integer friendJoinPolicy) {
        this.friendJoinPolicy = friendJoinPolicy;
    }

    /**
     * 用户状态：1-正常，2-异常（被平台封禁）
     */
    private void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * 应用appKey
     */
    private void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    /**
     * 是否是机器人：0-不是，1-是
     */
    private void setRobot(Integer robot) {
        this.robot = robot;
    }



    /**
     * 是否删除，1-已删除，0-未删除
     */
    private void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }


    /**
     * 主键id
     */
    private Long getId() {
        return this.id;
    }

    /**
     * 开放id
     */
    private String getOpenId() {
        return this.openId;
    }

    /**
     * 用户名称（对应于身份证）
     */
    private String getUsername() {
        return this.username;
    }

    /**
     * 用户名密码
     */
    private String getPassword() {
        return this.password;
    }

    /**
     * 用户别名
     */
    private String getNickName() {
        return this.nickName;
    }

    /**
     * 用户头像url
     */
    private String getAvatar() {
        return this.avatar;
    }

    /**
     * 座右铭/格言
     */
    private String getMotto() {
        return this.motto;
    }

    /**
     * 年龄
     */
    private Integer getAge() {
        return this.age;
    }

    /**
     * 性别：0-女，1-男，2-其他
     */
    private Integer getSex() {
        return this.sex;
    }

    /**
     * 邮箱
     */
    private String getEmail() {
        return this.email;
    }

    /**
     * 手机号（国内）
     */
    private String getPhoneNum() {
        return this.phoneNum;
    }

    /**
     * 身份证号码
     */
    private String getIdCardNum() {
        return this.idCardNum;
    }

    /**
     * 群邀请的应答策略：0-需要验证，1-自动通过
     */
    private Integer getGroupInvitePolicy() {
        return this.groupInvitePolicy;
    }

    /**
     * 好友添加的应答策略：0-需要验证，1-自动通过
     */
    private Integer getFriendJoinPolicy() {
        return this.friendJoinPolicy;
    }

    /**
     * 用户状态：1-正常，2-异常（被平台封禁）
     */
    private Integer getStatus() {
        return this.status;
    }

    /**
     * 应用appKey
     */
    private String getAppKey() {
        return this.appKey;
    }

    /**
     * 是否是机器人：0-不是，1-是
     */
    private Integer getRobot() {
        return this.robot;
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

    /**
     * 是否删除，1-已删除，0-未删除
     */
    private Integer getDeleted() {
        return this.deleted;
    }

}
