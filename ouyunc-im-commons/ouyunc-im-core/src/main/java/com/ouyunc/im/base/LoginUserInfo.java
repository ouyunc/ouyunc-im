package com.ouyunc.im.base;

import com.ouyunc.im.constant.enums.DeviceEnum;
import com.ouyunc.im.constant.enums.OnlineEnum;

import java.io.Serializable;

/**
 * @Author fangzhenxun
 * @Description: 用户登录相关信息
 * @Version V3.0
 **/
public class LoginUserInfo implements Serializable {
    private static final long serialVersionUID = 101;

    /**
     * 登录所属平台AppKey
     */
    private String appKey;

    /**
     * 登录唯一标识，用户id，手机号，身份证号码等
     */
    private String identity;

    /**
     * 登录的服务地址：host + port
     */
    private String loginServerAddress;

    /**
     * 在线状态，使用枚举
     */
    private OnlineEnum onlineStatus;


    /**
     * 登录设备，使用枚举
     */
    private DeviceEnum deviceEnum;


    //    /**
//     * 授权域，多个以逗号隔开，暂时不设计
//     */
//    private String authorizationScope;


    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

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

    public DeviceEnum getDeviceEnum() {
        return deviceEnum;
    }

    public void setDeviceEnum(DeviceEnum deviceEnum) {
        this.deviceEnum = deviceEnum;
    }

    public LoginUserInfo() {
    }


    public LoginUserInfo(String appKey, String identity, String loginServerAddress, OnlineEnum onlineStatus, DeviceEnum deviceEnum) {
        this.appKey = appKey;
        this.identity = identity;
        this.loginServerAddress = loginServerAddress;
        this.onlineStatus = onlineStatus;
        this.deviceEnum = deviceEnum;
    }
}
