package com.ouyunc.base.model;

/**
 * 客户端信息变更通知。业务写入 Redis 后发到 {@code client_info_publish_topic}。
 */
public class ClientInfoRefresh {

    private String appKey;

    private String identity;

    public ClientInfoRefresh() {
    }

    public ClientInfoRefresh(String appKey, String identity) {
        this.appKey = appKey;
        this.identity = identity;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }
}
