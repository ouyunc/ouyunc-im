package com.ouyunc.base.packet.message.content;


import com.ouyunc.base.model.ClientInfo;

import java.util.List;

/**
 * @Author fzx
 * @Description: 登录消息内容, 在登录的时候做校验
 **/
public class LoginContent extends ClientInfo {


    /**
     * 设备序列号
     */
    private String sn;

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



    public String getSn() {
        return sn;
    }

    public void setSn(String sn) {
        this.sn = sn;
    }

    public int getHeartBeatExpireTime() {
        return heartBeatExpireTime;
    }

    public void setHeartBeatExpireTime(int heartBeatExpireTime) {
        this.heartBeatExpireTime = heartBeatExpireTime;
    }


    public LoginContent() {
    }

    public LoginContent(String appKey, String identity,  List<Byte>  supportDeviceTypes  ,String sn, String signature, byte signatureAlgorithm, int heartBeatExpireTime,  long createTime) {
        super(appKey, identity, supportDeviceTypes);
        this.sn = sn;
        this.signature = signature;
        this.signatureAlgorithm = signatureAlgorithm;
        this.heartBeatExpireTime = heartBeatExpireTime;
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "LoginContent{" +
                "  appKey='" + super.getAppKey() + '\'' +
                ", identity='" + super.getIdentity() + '\'' +
                ", supportDeviceTypes='" + super.getSupportDeviceTypes() + '\'' +
                ", sn=" + sn +
                ", signature='" + signature + '\'' +
                ", signatureAlgorithm=" + signatureAlgorithm +
                ", heartBeatExpireTime=" + heartBeatExpireTime +
                ", createTime=" + createTime +
                '}';
    }
}
