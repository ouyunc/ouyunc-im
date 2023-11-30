package com.ouyunc.im.packet.message.content;

import io.netty.handler.codec.mqtt.MqttFixedHeader;

import java.io.Serializable;

/**
 * @Author fangzhenxun
 * @Description: mqtt消息内容， 其实这里使用mqttContent类是为了做适配MqttMessage 不能序列化无法在网络中传输，借助mqttContent
 * 来进行适配
 **/
public class MqttContent implements Serializable {
    private static final long serialVersionUID = 100015L;

    /**
     * 固定头
     */
    private MqttFixedHeader fixedHeader;

    /**
     * 可变头
     */
    private Object variableHeader;

    /**
     * 载荷
     */
    private Object payload;

    public MqttContent() {
    }

    public MqttContent(MqttFixedHeader fixedHeader, Object variableHeader, Object payload) {
        this.fixedHeader = fixedHeader;
        this.variableHeader = variableHeader;
        this.payload = payload;
    }

    public MqttFixedHeader getFixedHeader() {
        return fixedHeader;
    }

    public void setFixedHeader(MqttFixedHeader fixedHeader) {
        this.fixedHeader = fixedHeader;
    }

    public Object getVariableHeader() {
        return variableHeader;
    }

    public void setVariableHeader(Object variableHeader) {
        this.variableHeader = variableHeader;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "MqttContent{" +
                "  fixedHeader=" + fixedHeader.toString() +
                ", variableHeader=" + variableHeader.toString() +
                ", payload=" + payload.toString() +
                '}';
    }
}
