package com.ouyunc.im.packet.message.content;


import java.io.Serializable;

/**
 * @Author fangzhenxun
 * @Description: 登录消息内容, 在登录的时候做校验
 * @Version V3.0
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
}
