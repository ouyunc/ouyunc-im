package com.ouyunc.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

/**
 * im 用户
 */
@TableName("ouyunc_im_user")
public class ImUser implements Serializable {
    private static final long serialVersionUID = 200;

    /**
     * 消息id，对应packetId
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
     * 用户状态：0-正常，1-异常（被平台封禁）
     */
    private Integer status;


    /**
     * 好友添加的应答策略：0-需要验证，1-自动通过
     */
    private Integer friendJoinPolicy;


    /**
     * 群邀请的应答策略：0-需要验证，1-自动通过
     */
    private Integer groupInvitePolicy;
    /**
     * 创建时间
     */
    private String createTime;


    /**
     * 更新时间
     */
    private String updateTime;


    /**
     * 是否删除(0-否，1-是)
     */
    @TableLogic
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

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Integer getFriendJoinPolicy() {
        return friendJoinPolicy;
    }

    public void setFriendJoinPolicy(Integer friendJoinPolicy) {
        this.friendJoinPolicy = friendJoinPolicy;
    }

    public Integer getGroupInvitePolicy() {
        return groupInvitePolicy;
    }

    public void setGroupInvitePolicy(Integer groupInvitePolicy) {
        this.groupInvitePolicy = groupInvitePolicy;
    }

    public ImUser() {
    }

    public ImUser(Long id, String openId, String username, String password, String nickName, String email, String phoneNum, String idCardNum, String avatar, String motto, Integer age, Integer sex, Integer status, Integer friendJoinPolicy, Integer groupInvitePolicy, String createTime, String updateTime, Integer deleted) {
        this.id = id;
        this.openId = openId;
        this.username = username;
        this.password = password;
        this.nickName = nickName;
        this.email = email;
        this.phoneNum = phoneNum;
        this.idCardNum = idCardNum;
        this.avatar = avatar;
        this.motto = motto;
        this.age = age;
        this.sex = sex;
        this.status = status;
        this.friendJoinPolicy = friendJoinPolicy;
        this.groupInvitePolicy = groupInvitePolicy;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.deleted = deleted;
    }

    public ImUser(Long id, String openId, String username, String password, String nickName, String email, String phoneNum, String idCardNum, String avatar, String motto, Integer age, Integer sex, Integer status, String createTime, String updateTime) {
        this.id = id;
        this.openId = openId;
        this.username = username;
        this.password = password;
        this.nickName = nickName;
        this.email = email;
        this.phoneNum = phoneNum;
        this.idCardNum = idCardNum;
        this.avatar = avatar;
        this.motto = motto;
        this.age = age;
        this.sex = sex;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }
}
