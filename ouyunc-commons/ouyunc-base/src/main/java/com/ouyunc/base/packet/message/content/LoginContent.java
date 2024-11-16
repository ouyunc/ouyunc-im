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

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(DeviceType deviceType) {
        this.deviceType = deviceType;
    }

    public LoginContent() {
    }

    public LoginContent(String appKey, String identity, DeviceType deviceType, String signature, byte signatureAlgorithm, int heartBeatExpireTime,  long createTime) {
        this.appKey = appKey;
        this.identity = identity;
        this.deviceType = deviceType;
        this.signature = signature;
        this.signatureAlgorithm = signatureAlgorithm;
        this.heartBeatExpireTime = heartBeatExpireTime;
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
                ", createTime=" + createTime +
                '}';
    }
}
