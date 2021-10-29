package com.ouyu.im.constant.enums;

/**
 * @Author fangzhenxun
 * @Description: 网络类型枚举
 * @Version V1.0
 **/
public enum NetworkEnum {
    OTHER((byte)0, "other", "其他网络"),
    NET_WIFI((byte)1, "wifi", "wifi网络"),
    NET_2G((byte)2, "2g", "2g"),
    NET_3G((byte)3, "3g", "3g"),
    NET_4G((byte)4, "4g", "4g"),
    NET_5G((byte)5, "5g", "5g");


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
