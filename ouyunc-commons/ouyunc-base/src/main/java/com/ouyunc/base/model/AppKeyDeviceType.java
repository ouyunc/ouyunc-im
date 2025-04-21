package com.ouyunc.base.model;

import com.ouyunc.base.constant.enums.DeviceType;

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
    private Set<DeviceType> deviceTypes;


    public AppKeyDeviceType() {
    }

    public AppKeyDeviceType(String appKey, Set<DeviceType> deviceTypes) {
        this.appKey = appKey;
        this.deviceTypes = deviceTypes;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public Set<DeviceType> getDeviceTypes() {
        return deviceTypes;
    }

    public void setDeviceTypes(Set<DeviceType> deviceTypes) {
        this.deviceTypes = deviceTypes;
    }
}
