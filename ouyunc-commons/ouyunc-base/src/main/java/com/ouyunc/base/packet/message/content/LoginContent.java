package com.ouyunc.base.packet.message.content;


import java.io.Serial;
import java.io.Serializable;
import java.util.List;

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
     * 当前客户端所支持的可登录的设备类型，可以为空，如果为空则取该客户端所属appKey下的设备类型，注意，如果所支持的设备类型不为空，会进行校验，且值只能是appKey 下所支持设备类型的子集
     */
    private List<Byte> supportDeviceTypes;

    /**
     * 用户唯一标识：设备号 + 手机号，邮箱，身份证号码，token等，不为空
     */

    private String identity;

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

    public List<Byte> getSupportDeviceTypes() {
        return supportDeviceTypes;
    }

    public void setSupportDeviceTypes(List<Byte> supportDeviceTypes) {
        this.supportDeviceTypes = supportDeviceTypes;
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

    public LoginContent(String appKey, String identity, List<Byte> supportDeviceTypes,  String sn, String signature, byte signatureAlgorithm, int heartBeatExpireTime,  long createTime) {
        this.appKey = appKey;
        this.identity = identity;
        this.supportDeviceTypes = supportDeviceTypes;
        this.sn = sn;
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
                ", sn=" + sn +
                ", signature='" + signature + '\'' +
                ", signatureAlgorithm=" + signatureAlgorithm +
                ", heartBeatExpireTime=" + heartBeatExpireTime +
                ", createTime=" + createTime +
                '}';
    }
}
