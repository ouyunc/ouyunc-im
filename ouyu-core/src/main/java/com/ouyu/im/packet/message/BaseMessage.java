package com.ouyu.im.packet.message;

import com.ouyu.im.entity.RoutingTable;
import io.protostuff.Tag;

import java.io.Serializable;
import java.util.List;

/**
 * @Author fangzhenxun
 * @Description: 基础 message, 额外字段，内部使用，不对外开放,集群使用
 * @Version V1.0
 **/
public  class BaseMessage implements Serializable {

    /**
     * 当前重试次数,默认0
     */
    @Tag(10)
    private int currentRetry;

    /**
     * 消息路由表
     */
    @Tag(11)
    private List<RoutingTable> routingTables;

    /**
     * 该消息是否通过delivery集群内传递
     */
    @Tag(12)
    private boolean isDelivery;

    /**
     * @Author fangzhenxun
     * @Description 清空数据
     * @return void
     */
    public void clear() {
        this.currentRetry = 0;
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

    public boolean isDelivery() {
        return isDelivery;
    }

    public void setDelivery(boolean delivery) {
        isDelivery = delivery;
    }
}
