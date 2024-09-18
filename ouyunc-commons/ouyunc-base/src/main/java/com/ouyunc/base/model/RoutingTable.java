package com.ouyunc.base.model;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @Author fzx
 * @Description: 消息路由表
 **/
public class RoutingTable implements Serializable  {

    /**
     * 当前服务地址 ip:port
     */
    private String serverAddress;

    /**
     * 上一个服务地址 ip:port
     */
    private String preServerAddress;

    /**
     * “当前服务地址”已经路由过的服务地址,ip+port
     */
    private Set<String> routedServerAddresses;


    public String getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public Set<String> getRoutedServerAddresses() {
        return routedServerAddresses == null ? new HashSet<>():routedServerAddresses;
    }

    public void setRoutedServerAddresses(Set<String> routedServerAddresses) {
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

    public RoutingTable(String serverAddress, String preServerAddress, Set<String> routedServerAddresses) {
        this.serverAddress = serverAddress;
        this.preServerAddress = preServerAddress;
        this.routedServerAddresses = routedServerAddresses;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutingTable that = (RoutingTable) o;
        return Objects.equals(this.serverAddress, that.serverAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.serverAddress);
    }
}
