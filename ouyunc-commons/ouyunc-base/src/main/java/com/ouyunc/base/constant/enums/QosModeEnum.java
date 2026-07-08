package com.ouyunc.base.constant.enums;

import com.ouyunc.base.constant.NumberConstant;

/**
 * qos 模式
 */
public enum QosModeEnum {
    /**
     * 客户端模式
     */
    @Deprecated
    CLIENT(NumberConstant.NUMBER_1),

    /**
     * 服务端模式
     */
    SERVER(NumberConstant.NUMBER_2)
    ;
    private byte value;

    QosModeEnum(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    public void setValue(byte value) {
        this.value = value;
    }

    public static QosModeEnum prototype(byte value) {
        for (QosModeEnum qosModeEnum : QosModeEnum.values()) {
            if (qosModeEnum.getValue() == value) {
                return qosModeEnum;
            }
        }
        return null;
    }
}
