package com.ouyunc.base.model;


import com.ouyunc.base.constant.enums.OnlineEnum;
import com.ouyunc.base.packet.message.content.LoginContent;

import java.util.Collection;
import java.util.Objects;

/**
 * @author fzx
 * @description 登录的客户端信息
 */
public class LoginClientInfo extends LoginContent implements Protocol{

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
    private byte deviceType;

    /**
     * 授权域，多个以逗号隔开，暂时不设计
     */
    private String authorizationScope;

    /**
     * 服务端计算后的心跳超时时间，单位秒
     */
    private int heartBeatTimeout;

    /**
     * 最近一次登录时间戳
     */
    private long lastLoginTime;

    /**
     * 登录时所在 IM 节点 epoch，与节点租约比对判定该登录是否仍有效
     */
    private long nodeEpoch;

    /**
     * 协议类型
     */
    private byte protocol;

    /**
     * 协议版本号
     */
    private byte protocolVersion;

    @Override
    public byte getProtocol() {
        return protocol;
    }

    @Override
    public byte getProtocolVersion() {
        return protocolVersion;
    }

    public byte getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(byte deviceType) {
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

    public long getNodeEpoch() {
        return nodeEpoch;
    }

    public void setNodeEpoch(long nodeEpoch) {
        this.nodeEpoch = nodeEpoch;
    }

    public void setProtocol(byte protocol) {
        this.protocol = protocol;
    }

    public void setProtocolVersion(byte protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    /**
     * 写入 Redis 登录 String 的副本，只带路由、踢人、在线判定字段。
     * 签名、遗嘱/存活正文、设备列表仍在 Channel 属性上。
     */
    public LoginClientInfo copyForRedis() {
        LoginClientInfo copy = new LoginClientInfo();
        copyRedisLoginFields(copy);
        return copy;
    }

    protected void copyRedisLoginFields(LoginClientInfo copy) {
        copy.setAppKey(getAppKey());
        copy.setIdentity(getIdentity());
        copy.setSelfSync(getSelfSync());
        copy.setDeviceType(getDeviceType());
        copy.setSn(getSn());
        copy.setProtocol(getProtocol());
        copy.setProtocolVersion(getProtocolVersion());
        copy.setLoginServerAddress(getLoginServerAddress());
        copy.setOnlineStatus(getOnlineStatus());
        copy.setHeartBeatTimeout(getHeartBeatTimeout());
        copy.setLastLoginTime(getLastLoginTime());
        copy.setNodeEpoch(getNodeEpoch());
        copy.setScope(getScope());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoginClientInfo that)) return false;
        return Objects.equals(getAppKey(), that.getAppKey()) &&  Objects.equals(getIdentity(), that.getIdentity()) && Objects.equals(getDeviceType(), that.getDeviceType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getAppKey(), getIdentity(), getDeviceType());
    }


    public LoginClientInfo() {
    }

    public LoginClientInfo(byte protocol, byte protocolVersion, String loginServerAddress, OnlineEnum onlineStatus, String authorizationScope, int heartBeatTimeout, long lastLoginTime, String appKey, String identity, byte deviceType, Collection<Byte> supportDeviceTypes, String sn, String signature, byte signatureAlgorithm, int heartBeatExpireTime, long createTime, int enableWill, String willMessage, int enableAlive, String aliveMessage, int scope, int businessIdleSeconds, int heartBeatWaitRetry, int businessIdleCloseStrike) {
        super(appKey, identity, supportDeviceTypes, sn, signature, signatureAlgorithm, heartBeatExpireTime, createTime, enableWill, willMessage, enableAlive, aliveMessage, scope, businessIdleSeconds, heartBeatWaitRetry, businessIdleCloseStrike);
        this.protocol = protocol;
        this.protocolVersion = protocolVersion;
        this.deviceType = deviceType;
        this.loginServerAddress = loginServerAddress;
        this.onlineStatus = onlineStatus;
        this.authorizationScope = authorizationScope;
        this.heartBeatTimeout = heartBeatTimeout;
        this.lastLoginTime = lastLoginTime;
    }

}
