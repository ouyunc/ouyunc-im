package com.ouyunc.domain.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName(value = "ouyunc_im_user")
public class UserEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 主键id
     */
    @Id
    @TableId(type = IdType.ASSIGN_ID)
    private String id;


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
     * 用户类型，见 {@link com.ouyunc.base.constant.enums.UserTypeEnum}：-1-系统，0-机器人，1-真实用户。
     */
    @Field("type")
    private Integer type;

    /**
     * 外部用户关联ID
     */
    @Indexed
    @Field("external_id")
    private String externalId;

    /**
     * 微信统一账号
     */
    @Indexed
    @Field("union_id")
    private String unionId;

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
     * 软删除：0-未删除；非0-已删除（毫秒时间戳）。MySQL / Mongo 统一键名 del_flag。
     */
    @Field("del_flag")
    @TableField("del_flag")
    private Long delFlag;



    public static final class Fields {
        public static final String id = "id";
        public static final String code = "code";
        public static final String openId = "open_id";
        public static final String appKey = "app_key";
        public static final String type = "type";
        public static final String externalId = "external_id";
        public static final String unionId = "union_id";
        /** Mongo / 查询字段名，对应文档键 del_flag */
        public static final String delFlag = "del_flag";
    }

    public UserEntity() {
    }

    public UserEntity(String id, String openId, String code, String username, String password, String nickName, String avatar, String motto, Integer age, Integer sex, String email, String phoneNum, String idCardNo, Integer groupInvitePolicy, Integer friendJoinPolicy, Integer status, String appKey, Integer type, String externalId, String unionId, LocalDateTime createTime, LocalDateTime updateTime, Long delFlag) {
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
        this.type = type;
        this.externalId = externalId;
        this.unionId = unionId;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.delFlag = delFlag;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getUnionId() {
        return unionId;
    }

    public void setUnionId(String unionId) {
        this.unionId = unionId;
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

    public Long getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Long delFlag) {
        this.delFlag = delFlag;
    }
}
