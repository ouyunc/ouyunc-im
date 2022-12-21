package com.ouyunc.im.packet.message;

import com.ouyunc.im.constant.enums.DeviceEnum;
import com.ouyunc.im.base.RoutingTable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author fangzhenxun
 * @Description: 扩展消息 message, 额外字段，内部使用，不对外开放,集群使用
 * @Version V3.0
 **/
public class ExtraMessage implements Serializable {


    /**
     * 该消息是否通过delivery集群内传递
     */
    private boolean isDelivery;

    /**
     * 当前消息投递重试次数,默认0
     */
    private int currentRetry;

     /**
      * 消息发送者所在服务器地址,如果是消息在服务之间转发，则该地址是上个消息所经过的服务地址：ip:port
      */
    private String fromServerAddress;

     /**
     * 最终接收者所在服务器地址：ip:port
     */
    private String targetServerAddress;


    /**
     * 最终接收者当前所使用的的登录设备类型,需要在一开始调用方法的时候设置进来
     */
    private DeviceEnum deviceEnum;

    /**
     * 消息路由表
     */
    private List<RoutingTable> routingTables;


    /**
     * @Author fangzhenxun
     * @Description 清空数据
     * @return void
     */
    public void clear() {
        this.currentRetry = 0;
        this.routingTables = null;
        this.isDelivery = false;
        this.fromServerAddress = null;
        this.targetServerAddress = null;
        this.deviceEnum = null;
    }

    public int getCurrentRetry() {
        return currentRetry;
    }

    public void setCurrentRetry(int currentRetry) {
        this.currentRetry = currentRetry;
    }

    public List<RoutingTable> routingTables() {
        return routingTables == null ? new ArrayList<>(): routingTables;
    }

    public void setRoutingTables(List<RoutingTable> routingTables) {
        this.routingTables = routingTables;
    }

    public boolean isDelivery() {
        return isDelivery;
    }

    public void setDelivery(boolean delivery) {
        isDelivery = delivery;
    }

    public String getFromServerAddress() {
        return fromServerAddress;
    }

    public void setFromServerAddress(String fromServerAddress) {
        this.fromServerAddress = fromServerAddress;
    }

    public String getTargetServerAddress() {
        return targetServerAddress;
    }

    public void setTargetServerAddress(String targetServerAddress) {
        this.targetServerAddress = targetServerAddress;
    }

    public DeviceEnum getDeviceEnum() {
        return deviceEnum;
    }

    public void setDeviceEnum(DeviceEnum deviceEnum) {
        this.deviceEnum = deviceEnum;
    }

    public ExtraMessage() {
    }
}
