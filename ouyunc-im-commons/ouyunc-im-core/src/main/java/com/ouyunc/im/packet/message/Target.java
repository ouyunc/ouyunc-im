package com.ouyunc.im.packet.message;

import com.ouyunc.im.constant.enums.DeviceEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * 消息接收的目标
 */
public class Target implements Serializable,Cloneable {

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
    private DeviceEnum deviceEnum;

    public String getTargetIdentity() {
        return targetIdentity;
    }

    public String getTargetServerAddress() {
        return targetServerAddress;
    }

    public DeviceEnum getDeviceEnum() {
        return deviceEnum;
    }

    public void setTargetIdentity(String targetIdentity) {
        this.targetIdentity = targetIdentity;
    }

    public void setTargetServerAddress(String targetServerAddress) {
        this.targetServerAddress = targetServerAddress;
    }

    public void setDeviceEnum(DeviceEnum deviceEnum) {
        this.deviceEnum = deviceEnum;
    }

    private Target() {
    }

    @Override
    public Target clone(){
        Target o = null;
        try {
            o = (Target) super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return o;
    }

    public static Builder newBuilder(){
        return new Builder();
    }

    public static class Builder {
        private static Logger log = LoggerFactory.getLogger(Builder.class);

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
        private DeviceEnum deviceEnum;



        public Builder targetIdentity(String targetIdentity) {
            this.targetIdentity = targetIdentity;
            return this;
        }



        public Builder targetServerAddress(String targetServerAddress) {
            this.targetServerAddress = targetServerAddress;
            return this;
        }


        public Builder deviceEnum(DeviceEnum deviceEnum) {
            this.deviceEnum = deviceEnum;
            return this;
        }

        public Target build() {
            Target target = new Target();
            target.targetIdentity=this.targetIdentity;
            target.targetServerAddress = this.targetServerAddress;
            target.deviceEnum=this.deviceEnum;
            return target;
        }
    }

    @Override
    public String toString() {
        return "Target{" +
                "targetIdentity='" + targetIdentity + '\'' +
                ", targetServerAddress='" + targetServerAddress + '\'' +
                ", deviceEnum=" + deviceEnum +
                '}';
    }
}
