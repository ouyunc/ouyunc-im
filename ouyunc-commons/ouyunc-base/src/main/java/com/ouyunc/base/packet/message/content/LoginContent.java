package com.ouyunc.base.packet.message.content;


import com.ouyunc.base.model.ClientInfo;

import java.util.Collection;

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
     * 是否启用遗嘱: 1-启用，0-不启用，可以认为只要真实离线，并开启遗嘱就会发送遗嘱消息，与cleanSession无关
     */
    private int enableWill;

    /**
     * 遗嘱消息，客户端下线后，根据具体业务推送将该信息推送给相关联的人，可以是json格式字符串，具体看业务
     */
    private String willMessage;

    /**
     * 是否启用存活通知: 1-启用，0-不启用，在上线时推送该信息给所有关联的人
     */
    private int enableAlive;

    /**
     * 存活消息，客户端上线后，根据具体业务推送将该信息推送给相关联的人，可以是json格式字符串，具体看业务
     */
    private String aliveMessage;


    /**
     * 创建时间戳（毫秒）
     */

    private long  createTime;

    /**
     * 登录场景：0 普通；1 客服座席；2 客服访客。见 {@link com.ouyunc.base.constant.enums.LoginScopeEnum}
     */
    private int scope;

    /**
     * 业务空闲：无业务消息（非 PING）持续秒数；仅当 {@link #scope} 为客服且 {@code >0} 时安装业务空闲处理器。0 表示不启用。
     */
    private int businessIdleSeconds;

    /**
     * 读空闲侧关闭连接前的重试次数（与全局 heart-beat.wait-retry 同语义）；0 表示使用服务端全局 {@code ouyunc.message.client.heart-beat.wait-retry}
     */
    private int heartBeatWaitRetry;

    /**
     * 连续业务空闲达到第几次时在管道内关闭连接：{@code <=0}（含未传的默认 0）表示不因次数关连；{@code >=1} 表示第 N 次关连。
     */
    private int businessIdleCloseStrike;


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

    public int getEnableAlive() {
        return enableAlive;
    }

    public void setEnableAlive(int enableAlive) {
        this.enableAlive = enableAlive;
    }

    public String getAliveMessage() {
        return aliveMessage;
    }

    public void setAliveMessage(String aliveMessage) {
        this.aliveMessage = aliveMessage;
    }

    public int getEnableWill() {
        return enableWill;
    }

    public void setEnableWill(int enableWill) {
        this.enableWill = enableWill;
    }

    public String getWillMessage() {
        return willMessage;
    }

    public void setWillMessage(String willMessage) {
        this.willMessage = willMessage;
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

    public int getScope() {
        return scope;
    }

    public void setScope(int scope) {
        this.scope = scope;
    }

    public int getBusinessIdleSeconds() {
        return businessIdleSeconds;
    }

    public void setBusinessIdleSeconds(int businessIdleSeconds) {
        this.businessIdleSeconds = businessIdleSeconds;
    }

    public int getHeartBeatWaitRetry() {
        return heartBeatWaitRetry;
    }

    public void setHeartBeatWaitRetry(int heartBeatWaitRetry) {
        this.heartBeatWaitRetry = heartBeatWaitRetry;
    }

    public int getBusinessIdleCloseStrike() {
        return businessIdleCloseStrike;
    }

    public void setBusinessIdleCloseStrike(int businessIdleCloseStrike) {
        this.businessIdleCloseStrike = businessIdleCloseStrike;
    }


    public LoginContent() {
    }

    public LoginContent(String appKey, String identity, Collection<Byte> supportDeviceTypes, String sn, String signature, byte signatureAlgorithm, int heartBeatExpireTime, long createTime, int enableWill, String willMessage, int enableAlive, String aliveMessage, int scope, int businessIdleSeconds, int heartBeatWaitRetry, int businessIdleCloseStrike) {
        super(appKey, identity, supportDeviceTypes);
        this.sn = sn;
        this.signature = signature;
        this.signatureAlgorithm = signatureAlgorithm;
        this.heartBeatExpireTime = heartBeatExpireTime;
        this.createTime = createTime;
        this.enableWill = enableWill;
        this.willMessage = willMessage;
        this.enableAlive = enableAlive;
        this.aliveMessage = aliveMessage;
        this.scope = scope;
        this.businessIdleSeconds = businessIdleSeconds;
        this.heartBeatWaitRetry = heartBeatWaitRetry;
        this.businessIdleCloseStrike = businessIdleCloseStrike;
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
                ", enableWill=" + enableWill +
                ", willMessage='" + willMessage + '\'' +
                ", enableAlive='" + enableAlive + '\'' +
                ", aliveMessage='" + aliveMessage + '\'' +
                ", scope=" + scope +
                ", businessIdleSeconds=" + businessIdleSeconds +
                ", heartBeatWaitRetry=" + heartBeatWaitRetry +
                ", businessIdleCloseStrike=" + businessIdleCloseStrike +
                '}';
    }
}
