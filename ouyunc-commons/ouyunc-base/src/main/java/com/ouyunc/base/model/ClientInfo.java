package com.ouyunc.base.model;


import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * @author fzx
 * @description 公共客户端信息
 */
public class ClientInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1000L;


    /**
     * 平台签发的appKey,每个平台唯一标识，用来做认证，校验用户是否合法，不为空
     */

    private String appKey;


    /**
     * 用户唯一标识：设备号 + 手机号，邮箱，身份证号码，token等，不为空
     */

    private String identity;



    /**
     * 当前客户端所支持的可登录的设备类型，可以为空，如果为空则取该客户端所属appKey下的设备类型，注意，如果所支持的设备类型不为空，会进行校验，且值只能是appKey 下所支持设备类型的子集
     */
    private List<Byte> supportDeviceTypes;


    public ClientInfo() {
    }

    public ClientInfo(String appKey, String identity, List<Byte> supportDeviceTypes) {
        this.appKey = appKey;
        this.identity = identity;
        this.supportDeviceTypes = supportDeviceTypes;
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

    public List<Byte> getSupportDeviceTypes() {
        return supportDeviceTypes;
    }

    public void setSupportDeviceTypes(List<Byte> supportDeviceTypes) {
        this.supportDeviceTypes = supportDeviceTypes;
    }
}
