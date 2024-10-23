package com.ouyunc.base.constant.enums;

import io.netty.handler.codec.mqtt.*;

/**
 * @author fzx
 * @description mqtt消息内容类型枚举
 */
public enum MqttMessageContentTypeEnum implements MessageContentType {
    MQTT_CONNECT(51,ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), MqttMessageType.CONNECT.value(), MqttConnectMessage.class, "mqtt客户端connect消息内容"),
    MQTT_CONNECT_ACK(52,ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), MqttMessageType.CONNACK.value(), MqttConnAckMessage.class, "mqtt客户端connAck消息内容"),
    MQTT_PINGREQ(53,ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), MqttMessageType.PINGREQ.value(), MqttMessage.class, "mqtt客户端PINGREQ心跳消息内容"),
    MQTT_DISCONNECT(54,ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), MqttMessageType.DISCONNECT.value(), MqttMessage.class, "mqtt客户端DISCONNECT消息内容"),
    MQTT_UNSUBSCRIBE(55,ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), MqttMessageType.UNSUBSCRIBE.value(), MqttUnsubscribeMessage.class, "mqtt客户端Unsubscribe消息内容"),
    ;
    /**
     * 唯一标识code
     */
    private int type;

    private byte protocol;

    private byte protocolVersion;

    private int mqttMessageTypeValue;
    /**
     * 枚举对应的内容具体类
     */
    private Class<?> contentClass;
    /**
     * 描述
     */
    private String description;

    MqttMessageContentTypeEnum(int type, byte protocol, byte protocolVersion, int mqttMessageTypeValue, Class<?> contentClass, String description) {
        this.type = type;
        this.protocol = protocol;
        this.protocolVersion = protocolVersion;
        this.mqttMessageTypeValue = mqttMessageTypeValue;
        this.contentClass = contentClass;
        this.description = description;
    }

    @Override
    public byte getProtocol() {
        return protocol;
    }

    public void setProtocol(byte protocol) {
        this.protocol = protocol;
    }

    @Override
    public byte getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(byte protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Class<?> getContentClass() {
        return contentClass;
    }

    public void setContentClass(Class<?> contentClass) {
        this.contentClass = contentClass;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getMqttMessageTypeValue() {
        return mqttMessageTypeValue;
    }

    public void setMqttMessageTypeValue(int mqttMessageTypeValue) {
        this.mqttMessageTypeValue = mqttMessageTypeValue;
    }

    public static MqttMessageContentTypeEnum getMqttMessageContentTypeByMqttMessageTypeValue(int mqttMessageTypeValue) {
        for (MqttMessageContentTypeEnum mqttMessageContentType : MqttMessageContentTypeEnum.values()) {
            if (mqttMessageContentType.mqttMessageTypeValue == mqttMessageTypeValue) {
                return mqttMessageContentType;
            }
        }
        return null;
    }
}
