package com.ouyu.im.constant.enums;

/**
 * @Author fangzhenxun
 * @Description: 好友枚举
 * @Version V1.0
 **/
public enum  FriendEnum {

    REQ("REQ", "添加好友请求"),
    RESOLVE("RESOLVE", "同意添加"),
    REJECT("REJECT", "拒绝添加"),
    DELETE("DELETE", "删除好友");


    private String cmd;
    private String description;


    public String getCmd() {
        return cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    FriendEnum(String cmd, String description) {
        this.cmd = cmd;
        this.description = description;
    }
}
