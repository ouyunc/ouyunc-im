package com.ouyunc.base.constant.enums;

import com.ouyunc.base.constant.NumberConstant;
import io.netty.handler.codec.mqtt.*;

/**
 * @author fzx
 * @description mqtt消息内容类型枚举
 */
public enum MqttMessageContentTypeEnum implements MessageContentType {
    MQTT_CONNECT(NumberConstant.NUMBER_51,ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), MqttMessageType.CONNECT.value(), MqttConnectMessage.class, "mqtt客户端connect消息内容"),
    MQTT_CONNECT_ACK(NumberConstant.NUMBER_52,ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), MqttMessageType.CONNACK.value(), MqttConnAckMessage.class, "mqtt客户端connAck消息内容"),
    MQTT_PUBLISH(NumberConstant.NUMBER_53,ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), MqttMessageType.PUBLISH.value(), MqttPublishMessage.class, "mqtt客户端publish消息内容"),
    MQTT_PUBACK(NumberConstant.NUMBER_54,ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), MqttMessageType.PUBACK.value(), MqttPubAckMessage.class, "mqtt客户端PUBACK消息内容"),
    MQTT_PUBREC(NumberConstant.NUMBER_55,ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), MqttMessageType.PUBREC.value(), MqttMessage.class, "mqtt客户端PUBREC消息内容"),
    MQTT_PUBREL(NumberConstant.NUMBER_56,ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), MqttMessageType.PUBREL.value(), MqttMessage.class, "mqtt客户端PUBREL消息内容"),
    MQTT_PUBCOMP(NumberConstant.NUMBER_57,ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), MqttMessageType.PUBCOMP.value(), MqttMessage.class, "mqtt客户端PUBCOMP消息内容"),
    MQTT_SUBSCRIBE(NumberConstant.NUMBER_58,ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), MqttMessageType.SUBSCRIBE.value(), MqttSubscribeMessage.class, "mqtt客户端subscribe消息内容"),
    MQTT_SUBACK(NumberConstant.NUMBER_59,ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), MqttMessageType.SUBACK.value(), MqttSubAckMessage.class, "mqtt客户端SUBACK消息内容"),
    MQTT_UNSUBSCRIBE(NumberConstant.NUMBER_60,ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), MqttMessageType.UNSUBSCRIBE.value(), MqttUnsubscribeMessage.class, "mqtt客户端Unsubscribe消息内容"),
    MQTT_UNSUBACK(NumberConstant.NUMBER_61,ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), MqttMessageType.UNSUBACK.value(), MqttUnsubAckMessage.class, "mqtt客户端UNSUBACK消息内容"),
    MQTT_PINGREQ(NumberConstant.NUMBER_62,ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), MqttMessageType.PINGREQ.value(), MqttMessage.class, "mqtt客户端PINGREQ心跳消息内容"),
    MQTT_PINGRESP(NumberConstant.NUMBER_63,ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), MqttMessageType.PINGRESP.value(), MqttMessage.class, "mqtt客户端PINGRESP心跳消息响应内容"),
    MQTT_DISCONNECT(NumberConstant.NUMBER_64,ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), MqttMessageType.DISCONNECT.value(), MqttMessage.class, "mqtt客户端DISCONNECT消息内容"),
    MQTT_AUTH(NumberConstant.NUMBER_65,ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), MqttMessageType.AUTH.value(), MqttMessage.class, "mqtt客户端AUTH消息内容"),
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
