package com.ouyunc.base.constant.enums;


/**
 * @Author fzx
 * @Description: 客户端设备枚举, 注意如果想扩展设备枚举，可以在此类进行添加或者新建枚举类实现DeviceType接口并将实现类添加到   MessageServerContext。deviceTypeClassList中  请看： MessageServerContext
 **/
public enum DeviceTypeEnum implements DeviceType {

    OTHER((byte) 0, "other", "其他设备"),

    /**
     * 移动端
     */
    M_OTHER((byte) 1, "m", "移动端其他系统"),
    M_ANDROID((byte) 2, "m", "移动端安卓系统"),
    M_IOS((byte) 3, "m", "移动端ios(苹果)系统"),
    M_WINDOWS((byte) 4, "m", "移动端windows操作系统"),
    M_WEBOS((byte) 5, "m", "移动端Palm webOS是一个嵌入式操作系统"),
    M_MEEGO((byte) 6, "m", "移动端MeeGo是一种基于Linux的自由及开放源代码的便携设备操作系统"),

    M_ANDROID_APP((byte) 21, "m-app", "移动端安卓系统APP端"),
    M_ANDROID_H5((byte) 22, "m-h5", "移动端安卓系统H5端"),
    M_ANDROID_XCX((byte) 23, "m-xcx", "移动端安卓系统小程序端"),

    M_IOS_APP((byte) 31, "m-app", "移动端ios(苹果)系统APP"),
    M_IOS_H5((byte) 32, "m-h5", "移动端ios(苹果)系统H5"),
    M_IOS_XCX((byte) 33, "m-xcx", "移动端ios(苹果)系统小程序"),

    M_HARMONYOS_APP((byte) 41, "m-app", "移动端鸿蒙系统APP"),
    M_HARMONYOS_H5((byte) 42, "m-h5", "移动端鸿蒙系统H5"),
    M_HARMONYOS_XCX((byte) 43, "m-xcx", "移动端鸿蒙系统小程序"),

    /**
     * PC端
     */
    PC_OTHER((byte) 11, "pc", "电脑端其他系统"),
    PC_MAC((byte) 12, "pc", "电脑端苹果系统mac"),
    PC_WINDOWS((byte) 13, "pc", "电脑端windows系统"),
    PC_LINUX((byte) 14, "pc", "电脑端linux系统"),
    PC_HARMONYOS((byte) 15, "pc", "电脑端华为鸿蒙系统")


    // 可以扩展其他iot 等设备类型，如不同的品牌的设备对应相同的名称


    ;

    private byte value;
    private String name;
    private String description;

    DeviceTypeEnum(byte value, String name, String description) {
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

    public static DeviceTypeEnum getDeviceEnumByValue(byte value) {
        for (DeviceTypeEnum deviceEnum : DeviceTypeEnum.values()) {
            if (deviceEnum.value == value) {
                return deviceEnum;
            }
        }
        return null;
    }


    @Override
    public byte getDeviceTypeValue() {
        return value;
    }

    @Override
    public String getDeviceTypeName() {
        return name;
    }
}
