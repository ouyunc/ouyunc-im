package com.ouyu.im.entity;

import io.protostuff.Tag;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author fangzhenxun
 * @Description: 消息路由表
 * @Version V1.0
 **/
public class RoutingTable implements Serializable {

    /**
     * 当前服务地址
     */
    @Tag(1)
    private String serverAddress;

    /**
     * 上一个服务地址
     */
    @Tag(2)
    private String preServerAddress;

    /**
     * “当前服务地址”已经路由过的服务地址,host+port
     */
    @Tag(3)
    private List<String> routedServerAddresses;


    public String getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public List<String> getRoutedServerAddresses() {
        return routedServerAddresses;
    }

    public void setRoutedServerAddresses(List<String> routedServerAddresses) {
        this.routedServerAddresses = routedServerAddresses;
    }

    public String getPreServerAddress() {
        return preServerAddress;
    }

    public void setPreServerAddress(String preServerAddress) {
        this.preServerAddress = preServerAddress;
    }

    public RoutingTable() {
    }

    public RoutingTable(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public RoutingTable(String serverAddress, String preServerAddress, List<String> routedServerAddresses) {
        this.serverAddress = serverAddress;
        this.preServerAddress = preServerAddress;
        this.routedServerAddresses = routedServerAddresses;
    }


}
