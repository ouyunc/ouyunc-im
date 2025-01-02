package com.ouyunc.base.constant.enums;

import com.ouyunc.base.constant.NumberConstant;
import io.netty.handler.codec.mqtt.MqttQoS;

/**
 * qos 级别枚举
 */
public enum QosLevelEnum {

    QOS_0(NumberConstant.NUMBER_0, MqttQoS.AT_MOST_ONCE.value(), "QOS_0", "至多一次"),
    QOS_1(NumberConstant.NUMBER_1, MqttQoS.AT_LEAST_ONCE.value(), "QOS_1", "至少一次"),
    QOS_2(NumberConstant.NUMBER_2, MqttQoS.EXACTLY_ONCE.value(), "QOS_2", "仅一次"),
    QOS_3(NumberConstant.NUMBER_3, MqttQoS.FAILURE.value(), "QOS_3", "错误qos级别")

    ;


    private int level;

    private int mqttQosLevel;

    private String name;

    private String description;

    QosLevelEnum(int level, int mqttQosLevel, String name, String description) {
        this.level = level;
        this.mqttQosLevel = mqttQosLevel;
        this.name = name;
        this.description = description;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getMqttQosLevel() {
        return mqttQosLevel;
    }

    public void setMqttQosLevel(int mqttQosLevel) {
        this.mqttQosLevel = mqttQosLevel;
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

    public static QosLevelEnum getQosLevelEnumByMqttQosLevel(int mqttQosLevel) {
        for (QosLevelEnum qosLevelEnum : QosLevelEnum.values()) {
            if (qosLevelEnum.mqttQosLevel == mqttQosLevel) {
                return qosLevelEnum;
            }
        }
        return null;
    }

    public static QosLevelEnum getQosLevelEnum(int level) {
        for (QosLevelEnum qosLevelEnum : QosLevelEnum.values()) {
            if (qosLevelEnum.level == level) {
                return qosLevelEnum;
            }
        }
        return null;
    }
}
