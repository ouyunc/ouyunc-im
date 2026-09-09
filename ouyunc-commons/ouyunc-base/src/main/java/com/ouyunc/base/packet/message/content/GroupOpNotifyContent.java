package com.ouyunc.base.packet.message.content;

import java.io.Serial;
import java.io.Serializable;

/**
 * 群操作 SERVER_NOTIFY 协议体（contentType=-111）。不落聊天会话，仅实时通知客户端刷新群状态。
 */
public class GroupOpNotifyContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String OP_KICK = "KICK";
    public static final String OP_DISSOLVE = "DISSOLVE";
    public static final String OP_MUTE_ALL = "MUTE_ALL";
    public static final String OP_MUTE_MEMBER = "MUTE_MEMBER";
    public static final String OP_TRANSFER_OWNER = "TRANSFER_OWNER";
    public static final String OP_QUIT = "QUIT";
    public static final String OP_SET_ADMIN = "SET_ADMIN";
    public static final String OP_SHIELD = "SHIELD";

    /** 操作类型，见本类 OP_* 常量 */
    private String op;
    private String groupId;
    private String operatorId;
    private String targetUserId;
    /** 开/关类操作：禁言、设管理员、屏蔽 */
    private Boolean flag;

    public GroupOpNotifyContent() {
    }

    public GroupOpNotifyContent(String op, String groupId, String operatorId, String targetUserId, Boolean flag) {
        this.op = op;
        this.groupId = groupId;
        this.operatorId = operatorId;
        this.targetUserId = targetUserId;
        this.flag = flag;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public Boolean getFlag() {
        return flag;
    }

    public void setFlag(Boolean flag) {
        this.flag = flag;
    }
}
