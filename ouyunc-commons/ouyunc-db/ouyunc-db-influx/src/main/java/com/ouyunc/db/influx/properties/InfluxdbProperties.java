package com.ouyunc.db.influx.properties;

import java.io.Serializable;

/**
 * influx db 属性配置文件
 */
public class InfluxdbProperties implements Serializable {
    /**
     * 连接url
     */
    private String url;
    /**
     * 组织
     */
    private String org;
    /**
     * token
     */
    private String token;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getOrg() {
        return org;
    }

    public void setOrg(String org) {
        this.org = org;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
