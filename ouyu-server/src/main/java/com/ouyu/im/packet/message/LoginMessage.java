package com.ouyu.im.packet.message;

import io.protostuff.Tag;

import java.io.Serializable;

/**
 * @Author fangzhenxun
 * @Description: 登录消息, 在登录的时候做校验
 * @Version V1.0
 **/
public class LoginMessage extends Message implements Serializable {

    /**
     * 用户唯一标识，不为空
     */
    @Tag(1)
    private String identity;


    /**
     * 签发的appKey,每个平台唯一标识，不为空
     */
    @Tag(2)
    private String appKey;

    /**
     * 签名，通过一定算法与AppSecret一起计算得到的签名
     */
    @Tag(3)
    private String signature;

    /**
     * 生成签名的算法如：MD5,SHA1，SM3...
     */
    @Tag(4)
    private byte signatureAlgorithm;

    /**
     * 是否开启外部客户端与服务器的心跳，true-开启，false-关闭
     */
    @Tag(5)
    private boolean isOpenHeartBeat;


    /**
     * 心跳读超时时间，也就是服务端多久读不到来自客户端的消息的超时时间，单位秒；如果没有开启心跳则该字段不起作用
     */
    @Tag(6)
    private int heartBeatReadTimeout;

    /**
     * 创建时间戳（毫秒）
     */
    @Tag(7)
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

    public boolean isOpenHeartBeat() {
        return isOpenHeartBeat;
    }

    public void setOpenHeartBeat(boolean openHeartBeat) {
        isOpenHeartBeat = openHeartBeat;
    }

    public int getHeartBeatReadTimeout() {
        return heartBeatReadTimeout;
    }

    public void setHeartBeatReadTimeout(int heartBeatReadTimeout) {
        this.heartBeatReadTimeout = heartBeatReadTimeout;
    }
}
