package com.ouyunc.base.packet.message.content;

import java.io.Serializable;

/**
 * @author fzx
 * 群请求内容
 */
public class GroupRequestContent implements Serializable {

    /**
     * 唯一标识，针对同意/拒绝， 该字段是 申请人或被邀请人的唯一标识，  针对邀请，该字段是 被邀请人的唯一标识
     */
    private String identity;

    /**
     *
     * 邀请内容
     */
    private String content;

    public GroupRequestContent() {
    }


    public GroupRequestContent(String identity, String content) {
        this.identity = identity;
        this.content = content;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
