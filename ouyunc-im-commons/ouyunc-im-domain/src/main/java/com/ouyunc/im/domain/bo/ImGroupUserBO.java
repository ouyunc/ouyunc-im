package com.ouyunc.im.domain.bo;

/**
 * 该用户在群中的用户信息
 */
public class ImGroupUserBO {

    /**
     * 群组id
     */
    private Long groupId;

    /**
     * 群成员id
     */
    private Long userId;


    /**
     * 用户名称（对应于身份证）
     */
    private String username;



    /**
     * 用户昵称（用户在群里的昵称）
     */
    private String userNickName;

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
     * 是否是群主，0-否，1-是
     */
    private Integer isLeader;

    /**
     * 是否是群管理员，0-否，1-是
     */
    private Integer isManager;

    /**
     * 是否屏蔽群，0-未屏蔽，1-屏蔽
     */
    private Integer isShield;

    /**
     * 用户在群中的状态，0-正常，1-被禁言
     */
    private Integer mushin;


    /**
     * 加群时间
     */
    private String createTime;


    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
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

    public String getUserNickName() {
        return userNickName;
    }

    public void setUserNickName(String userNickName) {
        this.userNickName = userNickName;
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

    public Integer getIsLeader() {
        return isLeader;
    }

    public void setIsLeader(Integer isLeader) {
        this.isLeader = isLeader;
    }

    public Integer getIsManager() {
        return isManager;
    }

    public void setIsManager(Integer isManager) {
        this.isManager = isManager;
    }

    public Integer getIsShield() {
        return isShield;
    }

    public void setIsShield(Integer isShield) {
        this.isShield = isShield;
    }

    public Integer getMushin() {
        return mushin;
    }

    public void setMushin(Integer mushin) {
        this.mushin = mushin;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public ImGroupUserBO() {
    }

    public ImGroupUserBO(Long groupId, Long userId, String username, String userNickName, String email, String phoneNum, String idCardNum, String avatar, String motto, Integer age, Integer sex, Integer isLeader, Integer isManager, Integer isShield, Integer mushin, String createTime) {
        this.groupId = groupId;
        this.userId = userId;
        this.username = username;
        this.userNickName = userNickName;
        this.email = email;
        this.phoneNum = phoneNum;
        this.idCardNum = idCardNum;
        this.avatar = avatar;
        this.motto = motto;
        this.age = age;
        this.sex = sex;
        this.isLeader = isLeader;
        this.isManager = isManager;
        this.isShield = isShield;
        this.mushin = mushin;
        this.createTime = createTime;
    }
}
