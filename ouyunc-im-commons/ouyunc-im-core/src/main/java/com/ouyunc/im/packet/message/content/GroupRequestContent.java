package com.ouyunc.im.packet.message.content;

import java.io.Serializable;

/**
 * 群请求内容
 */
public class GroupRequestContent implements Serializable {
    private static final long serialVersionUID = 100001L;

    /**
     * 群组id
     */
    private String groupId;



    /**
     * 申请人唯一标识
     */
    private String identity;


    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }
}
