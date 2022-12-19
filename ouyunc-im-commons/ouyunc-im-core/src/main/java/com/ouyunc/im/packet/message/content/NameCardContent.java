package com.ouyunc.im.packet.message.content;

import java.io.Serializable;

/**
 * 名片内容
 */
public class NameCardContent implements Serializable {
    private static final long serialVersionUID = 100003L;

    /**
     * 个人或群唯一标识
     */
    private String identity;

    /**
     * 名称
     */
    private String name;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 描述
     */
    private String description;

    /**
     * 名片类型，1-个人， 2-群
     */
    private Integer type;


    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }
}
