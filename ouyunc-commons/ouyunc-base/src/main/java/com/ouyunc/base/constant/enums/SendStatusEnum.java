package com.ouyunc.base.constant.enums;

/**
 * @Author fzx
 * @Description: 发送状态
 **/
public enum SendStatusEnum {

    SEND_OK(1, "发送成功"),
    SEND_FAIL(0, "发送失败");

    /**
     * 状态
     */
    private int status;

    /**
     * 描述
     */
    private String description;

    SendStatusEnum(int status, String description) {
        this.status = status;
        this.description = description;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
