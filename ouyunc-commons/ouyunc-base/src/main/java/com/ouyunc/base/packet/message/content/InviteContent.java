package com.ouyunc.base.packet.message.content;

import java.io.Serializable;

/**
 * @author fzx
 * 邀请内容
 */
public class InviteContent implements Serializable {

    /**
     * 群id
     */
    private String groupId;

    /**
     * 邀请内容
     */
    private String content;

    public InviteContent() {
    }

    public InviteContent(String groupId, String content) {
        this.groupId = groupId;
        this.content = content;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
