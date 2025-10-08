package com.ouyunc.base.model;


import com.ouyunc.base.constant.enums.DeviceType;
import com.ouyunc.base.constant.enums.OnlineEnum;
import com.ouyunc.base.packet.message.content.LoginContent;

import java.util.Collection;
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
     * 当前登录的设备类型
     */
    private DeviceType deviceType;

    /**
     * 授权域，多个以逗号隔开，暂时不设计
     */
    private String authorizationScope;

    /**
     * 服务端计算后的心跳超时时间，单位秒
     */
    private int heartBeatTimeout;

    /**
     * 登录信息过期时间，单位秒
     */
    private long loginExpireTime;

    /**
     * 最近一次登录时间戳
     */
    private long lastLoginTime;

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(DeviceType deviceType) {
        this.deviceType = deviceType;
    }

    public int getHeartBeatTimeout() {
        return heartBeatTimeout;
    }

    public void setHeartBeatTimeout(int heartBeatTimeout) {
        this.heartBeatTimeout = heartBeatTimeout;
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

    public long getLoginExpireTime() {
        return loginExpireTime;
    }

    public void setLoginExpireTime(long loginExpireTime) {
        this.loginExpireTime = loginExpireTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoginClientInfo that)) return false;
        return Objects.equals(getAppKey(), that.getAppKey()) &&  Objects.equals(getIdentity(), that.getIdentity()) && Objects.equals(getDeviceType().getDeviceTypeValue(), that.getDeviceType().getDeviceTypeValue());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getAppKey(), getIdentity(), getDeviceType().getDeviceTypeValue());
    }


    public LoginClientInfo() {
    }

    public LoginClientInfo(String loginServerAddress, OnlineEnum onlineStatus, String authorizationScope, long loginExpireTime, int heartBeatTimeout, long lastLoginTime, String appKey, String identity, DeviceType deviceType, Collection<Byte> supportDeviceTypes, String sn, String signature, byte signatureAlgorithm, int heartBeatExpireTime, long createTime) {
        super(appKey, identity, supportDeviceTypes, sn, signature, signatureAlgorithm, heartBeatExpireTime, createTime);
        this.deviceType = deviceType;
        this.loginServerAddress = loginServerAddress;
        this.onlineStatus = onlineStatus;
        this.authorizationScope = authorizationScope;
        this.loginExpireTime = loginExpireTime;
        this.heartBeatTimeout = heartBeatTimeout;
        this.lastLoginTime = lastLoginTime;
    }
}
