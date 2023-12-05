package com.ouyunc.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

/**
 * 客户端发件箱
 */
@TableName("ouyunc_im_time_line")
public class ImTimeLine implements Serializable {
    private static final long serialVersionUID = 206;


    /**
     * 消息id，对应packetId
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 所属平台唯一标识
     */
    private String appKey;

    /**
     * 1个字节  协议类型,ws,http,自定义,默认为内部协议OUYUNC
     */
    private byte protocol;

    /**
     * 1个字节  协议版本，1,2,3
     */
    private byte protocolVersion;

    /**
     * 发送端设备类型 1个字节，m-android/m-ios/pc-windows/pc-mac/pad...
     */
    private byte deviceType;

    /**
     * 发送端设网络类型，1个字节：0-其他，1-有线， 2-wifi，3-5g, 4-4g, 5-3g, 6-2g
     */
    private byte networkType;


    /**
     * 消息加密，1个字节，加密方式，不加密/AES/...对称加密，防止消息泄密, 默认没有加密
     */
    private byte encryptType;

    /**
     * 序列化算法 1 个字节，json/jdk/hessian/kryo/protoStuff(protoBUf),默认是protobuf
     */
    private byte serializeAlgorithm;


    /**
     * 发送端ip4地址 4个字节，（目前如果需要支持ip6,则需要扩充存储字节数）
     */
    private String ip;


    /**
     * 发送者唯一标识
     */
    private String from;


    /**
     * 接收者唯一标识
     */
    private String to;


    /**
     * 消息类型：心跳，群聊，私聊...
     */
    private byte type;

    /**
     * 消息内容类型: 文本，图片，音频...
     */
    private Integer contentType;

    /**
     * 消息内容,json 字符串
     */
    private String content;


    /**
     * 扩展字段
     */
    private String extra;

    /**
     * 客户端发送时间，毫秒
     */
    private Long sendTime;

    /**
     * 消息是否撤回：0-未撤回，1-已撤回消息
     */
    private Integer withdraw;

    /**
     * 创建时间
     */
    private String createTime;


    /**
     * 更新时间
     */
    private String updateTime;


    /**
     * 是否删除(0-否，1-是)
     */
    @TableLogic
    private Integer deleted;

    public ImTimeLine() {
    }

    public ImTimeLine(Long id, byte protocol, byte protocolVersion, byte deviceType, byte networkType, byte encryptType, byte serializeAlgorithm, String ip, String from, String to, byte type, Integer contentType, String content, Long sendTime, String createTime, String updateTime, Integer deleted) {
        this.id = id;
        this.protocol = protocol;
        this.protocolVersion = protocolVersion;
        this.deviceType = deviceType;
        this.networkType = networkType;
        this.encryptType = encryptType;
        this.serializeAlgorithm = serializeAlgorithm;
        this.ip = ip;
        this.from = from;
        this.to = to;
        this.type = type;
        this.contentType = contentType;
        this.content = content;
        this.sendTime = sendTime;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.deleted = deleted;
    }

    public ImTimeLine(Long id, byte protocol, byte protocolVersion, byte deviceType, byte networkType, byte encryptType, byte serializeAlgorithm, String ip, String from, String to, byte type, Integer contentType, String content, Long sendTime) {
        this.id = id;
        this.protocol = protocol;
        this.protocolVersion = protocolVersion;
        this.deviceType = deviceType;
        this.networkType = networkType;
        this.encryptType = encryptType;
        this.serializeAlgorithm = serializeAlgorithm;
        this.ip = ip;
        this.from = from;
        this.to = to;
        this.type = type;
        this.contentType = contentType;
        this.content = content;
        this.sendTime = sendTime;
    }

    public ImTimeLine(Long id, String appKey, byte protocol, byte protocolVersion, byte deviceType, byte networkType, byte encryptType, byte serializeAlgorithm, String ip, String from, String to, byte type, Integer contentType, String content, String extra, Long sendTime, Integer withdraw, String createTime, String updateTime, Integer deleted) {
        this.id = id;
        this.appKey = appKey;
        this.protocol = protocol;
        this.protocolVersion = protocolVersion;
        this.deviceType = deviceType;
        this.networkType = networkType;
        this.encryptType = encryptType;
        this.serializeAlgorithm = serializeAlgorithm;
        this.ip = ip;
        this.from = from;
        this.to = to;
        this.type = type;
        this.contentType = contentType;
        this.content = content;
        this.extra = extra;
        this.sendTime = sendTime;
        this.withdraw = withdraw;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.deleted = deleted;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public byte getProtocol() {
        return protocol;
    }

    public void setProtocol(byte protocol) {
        this.protocol = protocol;
    }

    public byte getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(byte protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public byte getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(byte deviceType) {
        this.deviceType = deviceType;
    }

    public byte getNetworkType() {
        return networkType;
    }

    public void setNetworkType(byte networkType) {
        this.networkType = networkType;
    }

    public byte getEncryptType() {
        return encryptType;
    }

    public void setEncryptType(byte encryptType) {
        this.encryptType = encryptType;
    }

    public byte getSerializeAlgorithm() {
        return serializeAlgorithm;
    }

    public void setSerializeAlgorithm(byte serializeAlgorithm) {
        this.serializeAlgorithm = serializeAlgorithm;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public byte getType() {
        return type;
    }

    public void setType(byte type) {
        this.type = type;
    }

    public Integer getContentType() {
        return contentType;
    }

    public void setContentType(Integer contentType) {
        this.contentType = contentType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public Long getSendTime() {
        return sendTime;
    }

    public void setSendTime(Long sendTime) {
        this.sendTime = sendTime;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Integer getWithdraw() {
        return withdraw;
    }

    public void setWithdraw(Integer withdraw) {
        this.withdraw = withdraw;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }
}
