package com.ouyunc.im.packet.message.content;

import java.io.Serializable;
import java.util.List;

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
     * 申请人/邀请人唯一标识
     */
    private String identity;


    /**
     * 被邀请人,用户id列表
     */
    private List<String> invitedUserIdList;

    /**
     * 申请人发送的信息数据
     */
    private String data;


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

    public List<String> getInvitedUserIdList() {
        return invitedUserIdList;
    }

    public void setInvitedUserIdList(List<String> invitedUserIdList) {
        this.invitedUserIdList = invitedUserIdList;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
