package com.ouyu.im.constant.enums;


/**
 * @Author fangzhenxun
 * @Description: 客户端设备枚举
 * @Version V1.0
 **/
public enum DeviceEnum {
    /**
     * 移动端
     */
    M_ANDROID((byte)1, "m_android", "移动端安卓系统"),
    M_IOS((byte)2, "m_ios", "移动端ios(苹果)系统"),
    M_WINDOWS((byte)3, "m_windows", "移动端windows操作系统"),
    M_WEBOS((byte)4, "m_webos", "移动端Palm webOS是一个嵌入式操作系统"),
    M_MEEGO((byte)5, "m_meego", "移动端MeeGo是一种基于Linux的自由及开放源代码的便携设备操作系统"),
    /**
     * PC端
     */
    PC_MAC((byte)6, "pc_mac", "电脑端苹果系统mac"),
    PC_WINDOWS((byte)7, "pc_windows", "电脑端windows系统"),
    PC_LINUX((byte)8, "pc_linux", "电脑端linux系统"),
    PC_HARMONYOS((byte)9, "pc_harmonyos", "电脑端华为鸿蒙系统"),

    /**
     * 内部客户端所使用的设备
     */
    NONE((byte)10, "none", "内部客户端所使用的设备");

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
}
