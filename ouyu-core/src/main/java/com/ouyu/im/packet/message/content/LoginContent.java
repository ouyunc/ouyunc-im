package com.ouyu.im.packet.message.content;


import java.io.Serializable;

/**
 * @Author fangzhenxun
 * @Description: 登录消息, 在登录的时候做校验
 * @Version V1.0
 **/
public class LoginContent implements Serializable {

    /**
     * 用户唯一标识，不为空
     */

    private String identity;


    /**
     * 签发的appKey,每个平台唯一标识，不为空
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
