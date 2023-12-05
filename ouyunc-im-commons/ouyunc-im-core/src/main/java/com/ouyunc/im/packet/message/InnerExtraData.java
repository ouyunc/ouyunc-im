package com.ouyunc.im.packet.message;

import com.ouyunc.im.base.RoutingTable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author fangzhenxun
 * @Description: 扩展消息 message, 额外字段数据，内部使用，不对外开放,集群使用
 **/
public class InnerExtraData implements Serializable {


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
     * 平台唯一标识
     */
    private String appKey;

    /**
     * 发送消息的目标信息
     */
    private Target target;

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
        this.appKey = null;
        this.target=null;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public Target getTarget() {
        return target;
    }

    public void setTarget(Target target) {
        this.target = target;
    }

    public int getCurrentRetry() {
        return currentRetry;
    }

    public void setCurrentRetry(int currentRetry) {
        this.currentRetry = currentRetry;
    }

    public List<RoutingTable> getRoutingTables() {
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

    public InnerExtraData(boolean isDelivery, int currentRetry, String fromServerAddress, String appKey, Target target, List<RoutingTable> routingTables) {
        this.isDelivery = isDelivery;
        this.currentRetry = currentRetry;
        this.fromServerAddress = fromServerAddress;
        this.appKey = appKey;
        this.target = target;
        this.routingTables = routingTables;
    }

    public InnerExtraData() {
    }
}
