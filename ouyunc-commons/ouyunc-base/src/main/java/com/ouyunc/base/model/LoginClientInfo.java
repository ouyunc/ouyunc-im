package com.ouyunc.base.model;


import com.ouyunc.base.constant.enums.DeviceType;
import com.ouyunc.base.constant.enums.OnlineEnum;
import com.ouyunc.base.packet.message.content.LoginContent;

import java.util.Objects;

/**
 * @author fzx
 * @description 登录的客户端信息
 */
public class LoginClientInfo extends LoginContent {

    /**
     * 登录的服务地址：host + port
     */
    private String loginServerAddress;

    /**
     * 在线状态，使用枚举
     */
    private OnlineEnum onlineStatus;


    /**
     * 授权域，多个以逗号隔开，暂时不设计
     */
    private String authorizationScope;

    /**
     * 最近一次登录时间戳
     */
    private long lastLoginTime;


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

    public String getAuthorizationScope() {
        return authorizationScope;
    }

    public void setAuthorizationScope(String authorizationScope) {
        this.authorizationScope = authorizationScope;
    }

    public long getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(long lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoginClientInfo that)) return false;
        return Objects.equals(getAppKey(), that.getAppKey()) &&  Objects.equals(getIdentity(), that.getIdentity()) && Objects.equals(getDeviceType().getDeviceTypeName(), that.getDeviceType().getDeviceTypeName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getAppKey(), getIdentity(), getDeviceType().getDeviceTypeName());
    }


    public LoginClientInfo() {
    }

    public LoginClientInfo(String loginServerAddress, OnlineEnum onlineStatus, String authorizationScope, long lastLoginTime, String appKey, String identity, DeviceType deviceType, String signature, byte signatureAlgorithm, int heartBeatExpireTime, int enableWill, String willMessage, String willTopic, int cleanSession, int sessionExpiryInterval, long createTime) {
        super(appKey, identity, deviceType, signature, signatureAlgorithm, heartBeatExpireTime, enableWill,  willMessage, willTopic, cleanSession, sessionExpiryInterval, createTime);
        this.loginServerAddress = loginServerAddress;
        this.onlineStatus = onlineStatus;
        this.authorizationScope = authorizationScope;
        this.lastLoginTime = lastLoginTime;
    }
}
