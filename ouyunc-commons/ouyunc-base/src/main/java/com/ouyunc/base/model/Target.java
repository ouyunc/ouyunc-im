package com.ouyunc.base.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * 消息接收目标，仅描述业务身份与集群落点。
 *
 * <p>线协议属于最终建立连接的 Channel 上下文，不属于可跨节点传递的路由数据。
 * 因此本对象不得保存协议类型或版本，避免源节点携带的过期协议覆盖落地节点的真实连接协议。</p>
 */
public class Target implements Serializable, Cloneable {
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
     * 接收者所登录的服务器地址：ip:port。集群中转时保持为最终落地机，不是 hop。
     */
    private String targetServerAddress;

    /**
     * 接收者当前所使用的的登录设备类型,需要在一开始调用方法的时候设置进来
     */
    private byte deviceType;

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

        public Target build() {
            Target target = new Target();
            target.appKey = this.appKey;
            target.targetIdentity = this.targetIdentity;
            target.targetServerAddress = this.targetServerAddress;
            target.deviceType = this.deviceType;
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
                '}';
    }
}
