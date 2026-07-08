package com.ouyunc.base.constant.enums;

import com.ouyunc.base.constant.NumberConstant;

/**
 * @Author fzx
 * @Description: 网络类型枚举
 **/
public enum NetworkEnum {
    OTHER(NumberConstant.NUMBER_0, "other", "其他网络"),
    NET_WIFI(NumberConstant.NUMBER_1, "wifi", "wifi网络"),
    NET_2G(NumberConstant.NUMBER_2, "2g", "2g"),
    NET_3G(NumberConstant.NUMBER_3, "3g", "3g"),
    NET_4G(NumberConstant.NUMBER_4, "4g", "4g"),
    NET_5G(NumberConstant.NUMBER_5, "5g", "5g"),
    NET_6G(NumberConstant.NUMBER_6, "6g", "6g");


    private byte value;
    private String name;
    private String description;

    NetworkEnum(byte value, String name, String description) {
        this.value = value;
        this.name = name;
        this.description = description;
    }

    public byte getValue() {
        return value;
    }

    public void setValue(byte value) {
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public static NetworkEnum getNetworkEnumByValue(byte value) {
        for (NetworkEnum networkEnum : NetworkEnum.values()) {
            if (networkEnum.value == value) {
                return networkEnum;
            }
        }
        return null;
    }
}
