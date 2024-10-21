package com.ouyunc.repository;

import com.ouyunc.base.packet.Packet;
import io.netty.handler.codec.mqtt.MqttMessage;

/**
 * mqtt 消息持久化操作
 */
public class MqttRepository implements Repository{


    @Override
    public void save(Packet packet) {

    }


    public void saveWillMessage(String comboIdentity, MqttMessage mqttMessage) {

    }
}
