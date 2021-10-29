package com.ouyu.im.entity;

import com.ouyu.im.constant.enums.OnlineEnum;

import java.io.Serializable;

/**
 * @Author fangzhenxun
 * @Description: 用户登录相关信息
 * @Version V1.0
 **/
public class LoginUserInfo extends BaseUserInfo implements Serializable {


    /**
     * 登录的服务地址：host + port
     */
    private String loginServerAddress;

    /**
     * 在线状态，使用枚举
     */
    private OnlineEnum onlineStatus;


    public String getLoginServerAddress() {
        return loginServerAddress;
    }

    public void setLoginServerAddress(String loginServerAddress) {
        this.loginServerAddress = loginServerAddress;
    }

    public OnlineEnum getOnlineStatus() {
        return onlineStatus;
    }

    public void setOnlineStatus(OnlineEnum onlineStatus) {
        this.onlineStatus = onlineStatus;
    }

    public LoginUserInfo(String loginServerAddress, OnlineEnum onlineStatus) {
        this.loginServerAddress = loginServerAddress;
        this.onlineStatus = onlineStatus;
    }

    public LoginUserInfo() {
    }
}
