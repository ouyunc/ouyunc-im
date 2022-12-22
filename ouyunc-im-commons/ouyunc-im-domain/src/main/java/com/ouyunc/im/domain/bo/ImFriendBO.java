package com.ouyunc.im.domain.bo;

/**
 * 好友业务bo
 */
public class ImFriendBO {

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 好友用户id
     */
    private Long friendUserId;

    /**
     * 好友备注昵称
     */
    private String friendNickName;

    /**
     * 好友用户名称（对应于身份证）
     */
    private String friendUsername;


    /**
     * 好友邮箱
     */
    private String friendEmail;

    /**
     * 好友手机号（国内）
     */
    private String friendPhoneNum;

    /**
     * 好友身份证号码
     */
    private String friendIdCardNum;


    /**
     * 好友用户头像url
     */
    private String friendAvatar;

    /**
     * 好友座右铭/格言
     */
    private String friendMotto;

    /**
     * 好友年龄
     */
    private Integer friendAge;

    /**
     * 好友性别：0-女，1-男，2-其他
     */
    private Integer friendSex;


    /**
     * 是否屏蔽该好友，0-未屏蔽，1-屏蔽
     */
    private Integer friendIsShield;


    /**
     * 好友创建时间
     */
    private String createTime;

    /**
     * 好友修改时间
     */
    private String updateTime;


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

    public String getFriendUsername() {
        return friendUsername;
    }

    public void setFriendUsername(String friendUsername) {
        this.friendUsername = friendUsername;
    }

    public String getFriendEmail() {
        return friendEmail;
    }

    public void setFriendEmail(String friendEmail) {
        this.friendEmail = friendEmail;
    }

    public String getFriendPhoneNum() {
        return friendPhoneNum;
    }

    public void setFriendPhoneNum(String friendPhoneNum) {
        this.friendPhoneNum = friendPhoneNum;
    }

    public String getFriendIdCardNum() {
        return friendIdCardNum;
    }

    public void setFriendIdCardNum(String friendIdCardNum) {
        this.friendIdCardNum = friendIdCardNum;
    }

    public String getFriendAvatar() {
        return friendAvatar;
    }

    public void setFriendAvatar(String friendAvatar) {
        this.friendAvatar = friendAvatar;
    }

    public String getFriendMotto() {
        return friendMotto;
    }

    public void setFriendMotto(String friendMotto) {
        this.friendMotto = friendMotto;
    }

    public Integer getFriendAge() {
        return friendAge;
    }

    public void setFriendAge(Integer friendAge) {
        this.friendAge = friendAge;
    }

    public Integer getFriendSex() {
        return friendSex;
    }

    public void setFriendSex(Integer friendSex) {
        this.friendSex = friendSex;
    }


    public Integer getFriendIsShield() {
        return friendIsShield;
    }

    public void setFriendIsShield(Integer friendIsShield) {
        this.friendIsShield = friendIsShield;
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
}