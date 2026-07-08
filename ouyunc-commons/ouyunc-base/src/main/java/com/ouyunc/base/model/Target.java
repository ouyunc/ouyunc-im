package com.ouyunc.base.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * 消息接收的目标
 */
public class Target implements Serializable, Cloneable, Protocol{
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 接收者所属平台 appKey
     */
    private String appKey;

    /**
     * 接收者唯一标识
     */
    private String targetIdentity;

    /**
     * 接收者所登录的服务器地址：ip:port
     */
    private String targetServerAddress;

    /**
     * 接收者当前所使用的的登录设备类型,需要在一开始调用方法的时候设置进来
     */
    private byte deviceType;

    private byte protocol;

    private byte protocolVersion;

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getTargetIdentity() {
        return targetIdentity;
    }

    public String getTargetServerAddress() {
        return targetServerAddress;
    }

    public byte getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(byte deviceType) {
        this.deviceType = deviceType;
    }

    public void setTargetIdentity(String targetIdentity) {
        this.targetIdentity = targetIdentity;
    }

    public void setTargetServerAddress(String targetServerAddress) {
        this.targetServerAddress = targetServerAddress;
    }

    public void setProtocol(byte protocol) {
        this.protocol = protocol;
    }

    public void setProtocolVersion(byte protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    private Target() {
    }


    public static Builder newBuilder(){
        return new Builder();
    }

    @Override
    public Target clone() {
        try {
            return (Target) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    @Override
    public byte getProtocol() {
        return protocol;
    }

    @Override
    public byte getProtocolVersion() {
        return protocolVersion;
    }

    public static class Builder {

        /**
         * 最终接收者所属平台 appKey
         */
        private String appKey;

        /**
         * 最终接收者唯一标识
         */
        private String targetIdentity;

        /**
         * 最终接收者所在服务器地址：ip:port
         */
        private String targetServerAddress;

        /**
         * 最终接收者当前所使用的的登录设备类型,需要在一开始调用方法的时候设置进来
         */
        private byte deviceType;

        /**
         * 协议类型
         */
        private byte protocol;

        /**
         * 协议版本号
         */
        private byte protocolVersion;

        public Builder appKey(String appKey) {
            this.appKey = appKey;
            return this;
        }

        public Builder targetIdentity(String targetIdentity) {
            this.targetIdentity = targetIdentity;
            return this;
        }



        public Builder targetServerAddress(String targetServerAddress) {
            this.targetServerAddress = targetServerAddress;
            return this;
        }


        public Builder deviceType(byte deviceType) {
            this.deviceType = deviceType;
            return this;
        }

        public Builder protocol(byte protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder protocolVersion(byte protocolVersion) {
            this.protocolVersion = protocolVersion;
            return this;
        }

        public Target build() {
            Target target = new Target();
            target.appKey = this.appKey;
            target.targetIdentity = this.targetIdentity;
            target.targetServerAddress = this.targetServerAddress;
            target.deviceType = this.deviceType;
            target.protocol = this.protocol;
            target.protocolVersion = this.protocolVersion;
            return target;
        }
    }

    @Override
    public String toString() {
        return "Target{" +
                "appKey='" + appKey + '\'' +
                ", targetIdentity='" + targetIdentity + '\'' +
                ", targetServerAddress='" + targetServerAddress + '\'' +
                ", deviceType=" + deviceType +
                ", protocol=" + protocol +
                ", protocolVersion=" + protocolVersion +
                '}';
    }
}
