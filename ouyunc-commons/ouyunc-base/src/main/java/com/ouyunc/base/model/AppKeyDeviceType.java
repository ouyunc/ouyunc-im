package com.ouyunc.base.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;


/**
 * appKey与设备类型对应关系
 */
public class AppKeyDeviceType implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * appKey
     */
    private String appKey;

    /**
     * 设备类型
     */
    private Set<Byte> deviceTypes;


    public AppKeyDeviceType() {
    }

    public AppKeyDeviceType(String appKey, Set<Byte> deviceTypes) {
        this.appKey = appKey;
        this.deviceTypes = deviceTypes;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public Set<Byte> getDeviceTypes() {
        return deviceTypes;
    }

    public void setDeviceTypes(Set<Byte> deviceTypes) {
        this.deviceTypes = deviceTypes;
    }
}
