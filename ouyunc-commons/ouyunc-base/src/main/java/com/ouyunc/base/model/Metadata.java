package com.ouyunc.base.model;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author fzx
 * @Description: 扩展消息 message, 额外字段数据，内部使用，不对外开放,集群使用
 **/
public class Metadata implements Serializable {


    /**
     * 平台唯一标识
     */
    private String appKey;

    /**
     * 该消息是否通过集群内路由传递
     */
    private boolean routed;

    /**
     * 当前消息投递重试次数,默认0
     */
    private int currentRetry;

     /**
      * 消息发送者所在服务器地址,如果是消息在服务之间转发，则该地址是上个消息所经过的服务地址：ip:port
      */
     private String fromServerAddress;

    /**
     * 发送消息的目标信息,(消息最终接收方的信息)
     */
    private Target target;

    /**
     * 消息路由表
     */
    private List<RoutingTable> routingTables;

    /**
     * 消息客户端真实ip
     */
    private String clientIp;

    /**
     * 消息首次到达服务时间戳
     */
    private long serverTime;


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

    public boolean isRouted() {
        return routed;
    }

    public void setRouted(boolean routed) {
        this.routed = routed;
    }

    public String getFromServerAddress() {
        return fromServerAddress;
    }

    public void setFromServerAddress(String fromServerAddress) {
        this.fromServerAddress = fromServerAddress;
    }

    public long getServerTime() {
        return serverTime;
    }

    public void setServerTime(long serverTime) {
        this.serverTime = serverTime;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public Metadata(String appKey, boolean routed, int currentRetry, String fromServerAddress, Target target, List<RoutingTable> routingTables, String clientIp, long serverTime) {
        this.appKey = appKey;
        this.routed = routed;
        this.currentRetry = currentRetry;
        this.fromServerAddress = fromServerAddress;
        this.target = target;
        this.routingTables = routingTables;
        this.clientIp = clientIp;
        this.serverTime = serverTime;
    }

    public Metadata() {
    }
}
