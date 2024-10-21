package com.ouyunc.base.packet.message.content;


import com.ouyunc.base.constant.enums.DeviceType;

import java.io.Serial;
import java.io.Serializable;

/**
 * @Author fzx
 * @Description: 登录消息内容, 在登录的时候做校验
 **/
public class LoginContent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1000L;



    /**
     * 平台签发的appKey,每个平台唯一标识，用来做认证，校验用户是否合法，不为空
     */

    private String appKey;


    /**
     * 用户唯一标识：设备号 + 手机号，邮箱，身份证号码，token等，不为空
     */

    private String identity;

    /**
     * 设备类型
     */
    private DeviceType deviceType;

    /**
     * 签名，通过一定算法与AppSecret一起计算得到的签名
     */

    private String signature;

    /**
     * 生成签名的算法如：MD5,SHA1，SM3...
     */

    private byte signatureAlgorithm;

    /**
     * 客户端心跳过期时间（读写空闲时间，如果为空则使用全局配置的读空闲时间）,单位秒
     */
    private int heartBeatExpireTime;

    /**
     * 是否启用遗嘱: 1-启用，0-不启用，可以认为只要真实离线，并开启遗嘱就会发送遗嘱消息，与cleanSession无关
     */
    private int enableWill;

    /**
     * 遗嘱消息，客户端下线后，根据具体业务推送将该信息推送给相关联的人，可以是json格式字符串，具体看业务
     */
    private String willMessage;


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

    /**
     * 创建时间戳（毫秒）
     */

    private long  createTime;

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public byte getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    public void setSignatureAlgorithm(byte signatureAlgorithm) {

        this.signatureAlgorithm = signatureAlgorithm;
    }

    public int getHeartBeatExpireTime() {
        return heartBeatExpireTime;
    }

    public void setHeartBeatExpireTime(int heartBeatExpireTime) {
        this.heartBeatExpireTime = heartBeatExpireTime;
    }

    public String getWillMessage() {
        return willMessage;
    }

    public void setWillMessage(String willMessage) {
        this.willMessage = willMessage;
    }

    public int getCleanSession() {
        return cleanSession;
    }

    public void setCleanSession(int cleanSession) {
        this.cleanSession = cleanSession;
    }

    public String getWillTopic() {
        return willTopic;
    }

    public void setWillTopic(String willTopic) {
        this.willTopic = willTopic;
    }

    public int getEnableWill() {
        return enableWill;
    }

    public void setEnableWill(int enableWill) {
        this.enableWill = enableWill;
    }

    public int getSessionExpiryInterval() {
        return sessionExpiryInterval;
    }

    public void setSessionExpiryInterval(int sessionExpiryInterval) {
        this.sessionExpiryInterval = sessionExpiryInterval;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(DeviceType deviceType) {
        this.deviceType = deviceType;
    }

    public LoginContent() {
    }

    public LoginContent(String appKey, String identity, DeviceType deviceType, String signature, byte signatureAlgorithm, int heartBeatExpireTime, int enableWill, String willMessage, String willTopic, int cleanSession, int sessionExpiryInterval, long createTime) {
        this.appKey = appKey;
        this.identity = identity;
        this.deviceType = deviceType;
        this.signature = signature;
        this.signatureAlgorithm = signatureAlgorithm;
        this.heartBeatExpireTime = heartBeatExpireTime;
        this.enableWill = enableWill;
        this.willMessage = willMessage;
        this.willTopic = willTopic;
        this.cleanSession = cleanSession;
        this.sessionExpiryInterval = sessionExpiryInterval;
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "LoginContent{" +
                "  appKey='" + appKey + '\'' +
                ", identity='" + identity + '\'' +
                ", deviceType=" + deviceType +
                ", signature='" + signature + '\'' +
                ", signatureAlgorithm=" + signatureAlgorithm +
                ", heartBeatExpireTime=" + heartBeatExpireTime +
                ", enableWill=" + enableWill +
                ", willMessage='" + willMessage + '\'' +
                ", willTopic='" + willTopic + '\'' +
                ", cleanSession=" + cleanSession +
                ", sessionExpiryInterval=" + sessionExpiryInterval +
                ", createTime=" + createTime +
                '}';
    }
}
