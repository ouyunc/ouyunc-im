package com.ouyunc.im.domain.bo;

/**
 * 个人或群黑名单
 */
public class ImBlacklistBO {
    /**
     * 群或客户端唯一标识
     */
    private Long identity;

    /**
     * 唯一标识类型，1-客户端唯一标识（用户），2-群唯一标识
     */
    private Integer identityType;

    /**
     * 被拉黑的客户端id
     */
    private Long userId;

    /**
     * 被拉黑的客户端用户名称（对应于身份证）
     */
    private String username;

    /**
     * 被拉黑的客户端用户别名（该别名是好友或群组中的别名）
     */
    private String nickName;

    /**
     * 被拉黑的客户端邮箱
     */
    private String email;

    /**
     * 被拉黑的客户端手机号（国内）
     */
    private String phoneNum;

    /**
     * 被拉黑的客户端身份证号码
     */
    private String idCardNum;


    /**
     * 被拉黑的客户端用户头像url
     */
    private String avatar;

    /**
     * 被拉黑的客户端座右铭/格言
     */
    private String motto;

    /**
     * 被拉黑的客户端年龄
     */
    private Integer age;

    /**
     * 被拉黑的客户端性别：0-女，1-男，2-其他
     */
    private Integer sex;

    /**
     * 被拉黑的客户端时间
     */
    private String createTime;

    public Long getIdentity() {
        return identity;
    }

    public void setIdentity(Long identity) {
        this.identity = identity;
    }

    public Integer getIdentityType() {
        return identityType;
    }

    public void setIdentityType(Integer identityType) {
        this.identityType = identityType;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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
}
