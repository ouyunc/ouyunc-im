package com.ouyunc.im.packet.message.content;


import java.io.Serializable;

/**
 * @Author fangzhenxun
 * @Description: 登录消息内容, 在登录的时候做校验
 **/
public class LoginContent implements Serializable {
    private static final long serialVersionUID = 100006L;

    /**
     * 用户唯一标识：设备号 + 手机号，邮箱，身份证号码，token等，不为空
     */

    private String identity;


    /**
     * 平台签发的appKey,每个平台唯一标识，用来做认证，校验用户是否合法，不为空
     */

    private String appKey;

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
     * 是否启用遗嘱: 1-启用，0-不启用
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
     */
    private int cleanSession;

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

    public LoginContent() {
    }

    public LoginContent(String identity, String appKey, String signature, byte signatureAlgorithm, int heartBeatExpireTime, long createTime) {
        this.identity = identity;
        this.appKey = appKey;
        this.signature = signature;
        this.signatureAlgorithm = signatureAlgorithm;
        this.heartBeatExpireTime = heartBeatExpireTime;
        this.createTime = createTime;
    }

    public LoginContent(String identity, String appKey, String signature, byte signatureAlgorithm, int heartBeatExpireTime, int enableWill, String willMessage, String willTopic, int cleanSession, long createTime) {
        this.identity = identity;
        this.appKey = appKey;
        this.signature = signature;
        this.signatureAlgorithm = signatureAlgorithm;
        this.heartBeatExpireTime = heartBeatExpireTime;
        this.enableWill = enableWill;
        this.willMessage = willMessage;
        this.willTopic = willTopic;
        this.cleanSession = cleanSession;
        this.createTime = createTime;
    }

    public LoginContent(String identity, String appKey, String signature, byte signatureAlgorithm, long createTime) {
        this.identity = identity;
        this.appKey = appKey;
        this.signature = signature;
        this.signatureAlgorithm = signatureAlgorithm;
        this.createTime = createTime;
    }
}
