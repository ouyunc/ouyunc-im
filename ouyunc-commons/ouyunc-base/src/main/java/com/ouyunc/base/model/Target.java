package com.ouyunc.base.model;

import com.ouyunc.base.constant.enums.DeviceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * 消息接收的目标
 */
public class Target implements Serializable{

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
    private DeviceType deviceType;

    public String getTargetIdentity() {
        return targetIdentity;
    }

    public String getTargetServerAddress() {
        return targetServerAddress;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(DeviceType deviceType) {
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

    public static class Builder {
        private static final Logger log = LoggerFactory.getLogger(Builder.class);

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
        private DeviceType deviceType;



        public Builder targetIdentity(String targetIdentity) {
            this.targetIdentity = targetIdentity;
            return this;
        }



        public Builder targetServerAddress(String targetServerAddress) {
            this.targetServerAddress = targetServerAddress;
            return this;
        }


        public Builder deviceType(DeviceType deviceType) {
            this.deviceType = deviceType;
            return this;
        }

        public Target build() {
            Target target = new Target();
            target.targetIdentity=this.targetIdentity;
            target.targetServerAddress = this.targetServerAddress;
            target.deviceType=this.deviceType;
            return target;
        }
    }

    @Override
    public String toString() {
        return "Target{" +
                "targetIdentity='" + targetIdentity + '\'' +
                ", targetServerAddress='" + targetServerAddress + '\'' +
                ", deviceType=" + deviceType +
                '}';
    }
}
