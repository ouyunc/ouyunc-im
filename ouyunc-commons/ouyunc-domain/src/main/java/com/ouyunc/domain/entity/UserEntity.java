package com.ouyunc.domain.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户表
 *
 * @TableName ouyunc_im_user
 */
@TableName(value = "ouyunc_im_user")
public class UserEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 主键id
     */
    @TableId(type = IdType.ASSIGN_ID)
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
     * 性别：0-女，1-男
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

    public String getIdCardNum() {
        return idCardNum;
    }

    public void setIdCardNum(String idCardNum) {
        this.idCardNum = idCardNum;
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
}
