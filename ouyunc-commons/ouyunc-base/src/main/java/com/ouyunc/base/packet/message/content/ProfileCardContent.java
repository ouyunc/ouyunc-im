package com.ouyunc.base.packet.message.content;

import java.io.Serial;
import java.io.Serializable;

/**
 * 推荐人/名片消息内容（{@link com.ouyunc.base.constant.enums.MessageContentTypeEnum#PROFILE_CARD_CONTENT}）。
 */
public class ProfileCardContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 被推荐人 IM identity / 业务 userId */
    private String userId;
    /** 展示昵称或姓名 */
    private String name;
    /** 头像 URL */
    private String avatarUrl;
    /** 职位、标签或简介短文案，如「售后专员」 */
    private String title;
    /** 角色类型：user（用户）/ agent（坐席）/ influencer（达人）/ merchant（商家）等 */
    private String refType;
    /** 推荐语，说明为何推荐此人（可选） */
    private String recommendText;
    /** 点击跳转个人主页或发起会话的 H5 / DeepLink（可选） */
    private String link;

    public ProfileCardContent() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getRefType() {
        return refType;
    }

    public void setRefType(String refType) {
        this.refType = refType;
    }

    public String getRecommendText() {
        return recommendText;
    }

    public void setRecommendText(String recommendText) {
        this.recommendText = recommendText;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }
}
