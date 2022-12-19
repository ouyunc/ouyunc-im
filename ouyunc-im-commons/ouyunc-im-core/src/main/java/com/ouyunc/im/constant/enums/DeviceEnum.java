package com.ouyunc.im.constant.enums;


/**
 * @Author fangzhenxun
 * @Description: 客户端设备枚举
 * @Version V3.0
 **/
public enum DeviceEnum {

    OTHER((byte)0, "other", "其他设备"),

    /**
     * 移动端
     */
    M_OTHER((byte)1, "m", "移动端其他系统"),
    M_ANDROID((byte)2, "m", "移动端安卓系统"),
    M_IOS((byte)3, "m", "移动端ios(苹果)系统"),
    M_WINDOWS((byte)4, "m", "移动端windows操作系统"),
    M_WEBOS((byte)5, "m", "移动端Palm webOS是一个嵌入式操作系统"),
    M_MEEGO((byte)6, "m", "移动端MeeGo是一种基于Linux的自由及开放源代码的便携设备操作系统"),

    /**
     * PC端
     */
    PC_OTHER((byte)11, "pc", "电脑端其他系统"),
    PC_MAC((byte)12, "pc", "电脑端苹果系统mac"),
    PC_WINDOWS((byte)13, "pc", "电脑端windows系统"),
    PC_LINUX((byte)14, "pc", "电脑端linux系统"),
    PC_HARMONYOS((byte)15, "pc", "电脑端华为鸿蒙系统");

    private byte value;
    private String name;
    private String description;

    DeviceEnum(byte value, String name, String description) {
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

    public static DeviceEnum getDeviceEnumByValue(byte value) {
        for (DeviceEnum deviceEnum : DeviceEnum.values()) {
            if (deviceEnum.value == value) {
                return deviceEnum;
            }
        }
        return null;
    }

    public static String getDeviceNameByValue(byte value) {
        for (DeviceEnum deviceEnum : DeviceEnum.values()) {
            if (deviceEnum.value == value) {
                return deviceEnum.name;
            }
        }
        return null;
    }
}
