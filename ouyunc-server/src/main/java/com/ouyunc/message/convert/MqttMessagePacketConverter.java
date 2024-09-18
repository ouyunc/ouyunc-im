package com.ouyunc.message.convert;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;

/**
 * @author fzx
 * @description mqtt 协议消息转换成packet
 */
public enum MqttMessagePacketConverter implements PacketConverter<MqttMessage>{
    INSTANCE
    ;
    /***
     * @author fzx
     * @description 需要处理元数据的初始化
     */
    @Override
    public Packet convertToPacket(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof MqttMessage mqttMessage) {
            mqttMessage.fixedHeader().messageType();
        }
        return null;
    }

    @Override
    public MqttMessage convertFromPacket(Packet packet) {
        // 如果该包是内部协议的包，则进行逻辑处理
        if ((NativePacketProtocol.MQTT.getProtocol() == packet.getProtocol() &&  NativePacketProtocol.MQTT.getProtocolVersion() == packet.getProtocolVersion()) || (NativePacketProtocol.MQTT_WS.getProtocol() == packet.getProtocol() && NativePacketProtocol.MQTT_WS.getProtocolVersion() == packet.getProtocolVersion())) {

        }
        return null;
    }
}
