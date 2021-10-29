package com.ouyu.im.entity;


import com.ouyu.im.constant.enums.LoginEnum;

import java.io.Serializable;

/**
 * @Author fangzhenxun
 * @Description: 认证的用户信息
 * @Version V1.0
 **/
public class ChannelUserInfo extends BaseUserInfo implements Serializable {


    /**
     * 用户登录状态，枚举
     */
    private LoginEnum loginStatus;


//    /**
//     * 授权域，多个以逗号隔开，暂时不设计
//     */
//    private String authorizationScope;


    public LoginEnum getLoginStatus() {
        return loginStatus;
    }

    public void setLoginStatus(LoginEnum loginStatus) {
        this.loginStatus = loginStatus;
    }

    public ChannelUserInfo() {
    }

    public ChannelUserInfo(LoginEnum loginStatus) {
        this.loginStatus = loginStatus;
    }
}
