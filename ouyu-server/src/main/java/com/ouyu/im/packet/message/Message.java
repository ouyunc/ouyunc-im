package com.ouyu.im.packet.message;

import com.ouyu.im.entity.RoutingTable;
import io.protostuff.Tag;

import java.io.Serializable;
import java.util.List;

/**
 * @Author fangzhenxun
 * @Description: 抽象message, 额外字段，内部使用，不对外开放
 * @Version V1.0
 **/
public  class Message implements Serializable {

    /**
     * 当前重试次数,默认0
     */
    @Tag(10)
    private int currentRetry;

    /**
     * 目标服务器地址
     */
    @Tag(11)
    private String targetServerAddress;

    /**
     * 消息路由表
     */
    @Tag(12)
    private List<RoutingTable> routingTables;

    /**
     * 该消息是否通过delivery集群内传递
     */
    @Tag(13)
    private boolean isDelivery;

    /**
     * @Author fangzhenxun
     * @Description 清空数据
     * @return void
     */
    public void clear() {
        this.currentRetry = 0;
        this.targetServerAddress = null;
        this.routingTables = null;
        this.isDelivery = false;
    }

    public int getCurrentRetry() {
        return currentRetry;
    }

    public void setCurrentRetry(int currentRetry) {
        this.currentRetry = currentRetry;
    }

    public List<RoutingTable> routingTables() {
        return routingTables;
    }

    public void setRoutingTables(List<RoutingTable> routingTables) {
        this.routingTables = routingTables;
    }

    public String getTargetServerAddress() {
        return targetServerAddress;
    }

    public void setTargetServerAddress(String targetServerAddress) {

        this.targetServerAddress = targetServerAddress;
    }

    public boolean isDelivery() {
        return isDelivery;
    }

    public void setDelivery(boolean delivery) {
        isDelivery = delivery;
    }
}
