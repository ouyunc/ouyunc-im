package com.ouyunc.repository;

import com.ouyunc.base.packet.Packet;
import io.netty.handler.codec.mqtt.MqttMessage;

/**
 * mqtt 消息持久化操作
 */
public class MqttRepository implements Repository{


    /**
     * 保存全量信息
     * @param packet
     */
    @Override
    public void save(Packet packet) {

    }


    /**
     * 保存遗嘱消息
     * @param comboIdentity
     * @param mqttMessage
     */
    public void saveWillMessage(String comboIdentity, MqttMessage mqttMessage) {

    }

    /**
     * 取消订阅
     * @param topic
     * @param comboIdentity
     */
    public void unSubscribe(String topic, String comboIdentity) {

    }
}
