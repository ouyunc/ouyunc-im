package com.ouyunc.base.model;

import java.util.Set;

/**
 * appKey 与 客户端 与设备类型对应关系
 */
public class ClientAppKeyDeviceType extends AppKeyDeviceType {


    /**
     * identity  客户端唯一标识
     */
    private String identity;

    public ClientAppKeyDeviceType() {
    }
    public ClientAppKeyDeviceType(String appKey, String identity, Set<Byte> deviceTypes) {
        super(appKey, deviceTypes);
        this.identity = identity;
    }

    public String getIdentity() {
        return identity;
    }
    public void setIdentity(String identity) {
        this.identity = identity;
    }
}
