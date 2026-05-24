package com.ouyunc.base.model;

import com.ouyunc.base.constant.enums.OnlineEnum;

import java.util.Collection;

/**
 * mqtt 登录客户端登录信息
 */
public class MqttLoginClientInfo extends LoginClientInfo{

    /**
     * 消息服务质量等级，0-至多一次，1-至少一次，2- exactly once，；如果开启了遗嘱，这里指遗嘱信息消息质量等级
     */
    private int qos;

    /**
     * 协议版本，3 = 3.1, 4 = 3.1.1，5 = 5.0
     */
    private byte version;

    /**
     * 是否保留遗嘱消息，0-不保留，1-保留
     */
    private int isWillRetain;


    /**
     * 遗嘱主题，客户端下线后，根据具体业务推送将遗嘱信息willMessage推送给订阅willTopic的客户端，可以是json格式字符串，具体看业务
     */
    private String willTopic;

    /**
     * 是否复用之前的会话信息（主要是业务相关），具体业务具体对待 0-不清除，1-清除
     * 可以理解成是否开启离线消息，默认为0-不开起，1-开启
     */
    private int cleanSession;

    /**
     * 它被用来指定会话在网络断开后能够在服务端保留的最长时间，单位秒，如果到达过期时间但网络连接仍未恢复，服务端就会丢弃对应的会话状态, 配合cleanSession 使用  0（不保存），-1(永久)，大于0（保持一段时间）
     * 可以理解成开启离线消息的有效期，单位秒；  0（不保存），-1(永久)，大于0（保持一段时间）
     */
    private int sessionExpiryInterval;


    public String getWillTopic() {
        return willTopic;
    }

    public void setWillTopic(String willTopic) {
        this.willTopic = willTopic;
    }

    public int getCleanSession() {
        return cleanSession;
    }

    public void setCleanSession(int cleanSession) {
        this.cleanSession = cleanSession;
    }

    public int getSessionExpiryInterval() {
        return sessionExpiryInterval;
    }

    public void setSessionExpiryInterval(int sessionExpiryInterval) {
        this.sessionExpiryInterval = sessionExpiryInterval;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    public byte getVersion() {
        return version;
    }

    public void setVersion(byte version) {
        this.version = version;
    }

    public int getIsWillRetain() {
        return isWillRetain;
    }

    public void setIsWillRetain(int isWillRetain) {
        this.isWillRetain = isWillRetain;
    }

    public MqttLoginClientInfo() {
    }

    public MqttLoginClientInfo(byte protocol, byte protocolVersion, String loginServerAddress, OnlineEnum onlineStatus, String authorizationScope, long loginExpireTime, int heartBeatTimeout, long lastLoginTime, String appKey, String identity, byte deviceType, Collection<Byte> supportDeviceTypes, String sn, String signature, byte signatureAlgorithm, int heartBeatExpireTime, long createTime, int enableWill, int qos, byte version, int isWillRetain, String willMessage, String willTopic, int cleanSession, int sessionExpiryInterval, int enableAlive, String aliveMessage) {
        super(protocol, protocolVersion, loginServerAddress, onlineStatus, authorizationScope, loginExpireTime, heartBeatTimeout, lastLoginTime, appKey, identity, deviceType, supportDeviceTypes, sn, signature, signatureAlgorithm, heartBeatExpireTime, createTime, enableWill, willMessage, enableAlive, aliveMessage, 0, 0, 0, 0);
        this.qos = qos;
        this.version = version;
        this.isWillRetain = isWillRetain;
        this.willTopic = willTopic;
        this.cleanSession = cleanSession;
        this.sessionExpiryInterval = sessionExpiryInterval;
    }
}
