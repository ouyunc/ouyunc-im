package com.ouyunc.message.convert;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.DeviceTypeEnum;
import com.ouyunc.base.constant.enums.MqttMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MqttMessageTypeEnum;
import com.ouyunc.base.constant.enums.NetworkEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.Protocol;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IpUtil;
import com.ouyunc.base.utils.MqttCodecUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * @author fzx
 * @description mqtt 协议消息转换成packet
 */
public enum MqttMessagePacketConverter implements PacketConverter<MqttMessage>{
    INSTANCE
    ;
    private static final Logger log = LoggerFactory.getLogger(MqttMessagePacketConverter.class);

    /***
     * @author fzx
     * @description 需要处理元数据的初始化
     */
    @Override
    public Packet convertToPacket(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof MqttMessage mqttMessage) {
            // 集群环境下，消息路由不会走这个转换器
            MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(ctx);
            if (mqttVersion == null) {
                log.warn("暂不支持该协议：protocol={}", mqttVersion);
                return null;
            }
            Protocol protocol = ctx.channel().attr(NativePacketProtocol.protocolAttrKey).get();
            byte protocolValue = protocol.getProtocol();
            byte protocolVersion = protocol.getProtocolVersion();
            // 将mqttMessage 转换成 byte[]
            String mqttMessageBase64Content = MqttCodecUtil.encode(mqttVersion, mqttMessage);
            // 封装消息进行处理
            MqttFixedHeader mqttFixedHeader = mqttMessage.fixedHeader();
            MqttMessageType mqttMessageType = mqttFixedHeader.messageType();
            // 处理元数据
            Metadata metadata = new Metadata();
            // 判断如果不是集群中的传递消息，则进行以下处理
            if (!metadata.isRouted()) {
                // 设置该消息发送者当前登录所属的平台 appKey
                // 设置默认的appKey
                if (MqttMessageType.CONNECT == mqttMessageType && mqttMessage instanceof MqttConnectMessage mqttConnectMessage) {
                    // username 就是appKey
                    metadata.setAppKey(mqttConnectMessage.payload().userName());
                }else {
                    // 不是登录类型的消息，说明该客户端已经登录，可以从当前通道获取用户appKey
                    LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
                    metadata.setAppKey(loginClientInfo.getAppKey());
                }
                // 获取客户端真实ip
                metadata.setClientIp(IpUtil.getIp(ctx));
                // 设置服务器时间
                metadata.setServerTime(TimeUtil.currentTimeMillis());
            }
            String from;
            if (MqttMessageType.CONNECT.equals(mqttMessageType) && mqttMessage instanceof MqttConnectMessage mqttConnectMessage) {
                from = mqttConnectMessage.payload().clientIdentifier();
            }else {
                LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
                if (loginClientInfo == null) {
                    log.error("mqtt 登录信息不存在！");
                    return null;
                }
                from = loginClientInfo.getIdentity();
            }
            MqttMessageContentTypeEnum mqttMessageContentType = MqttMessageContentTypeEnum.getMqttMessageContentTypeByMqttMessageTypeValue(mqttMessageType.value());
            if (mqttMessageContentType == null) {
                log.error("暂不支持该消息类型：type={}", mqttMessageType);
                return null;
            }
            // 根据消息类型设置from 和 to
            Message message = new Message(from, MessageServerContext.serverProperties().getLocalServerAddress(), mqttMessageContentType.getType(), mqttMessageBase64Content , mqttFixedHeader.qosLevel().value(), TimeUtil.currentTimeMillis(), metadata);
            return new Packet(protocolValue, protocolVersion, MessageServerContext.<Long>idGenerator().generateId(), DeviceTypeEnum.IOT.getValue(), NetworkEnum.NET_4G.getValue(), Encrypt.SymmetryEncrypt.NONE.getValue(), Serializer.PROTO_STUFF.getValue(), MqttMessageTypeEnum.MQTT.getType(), mqttVersion.protocolLevel(), message);
        }
        return null;
    }

    @Override
    public MqttMessage convertFromPacket(Packet packet) {
        // 如果该包是内部协议的包，则进行逻辑处理
        if (NativePacketProtocol.MQTT.getProtocol() == packet.getProtocol() && NativePacketProtocol.MQTT.getProtocolVersion() == packet.getProtocolVersion()) {
            MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(packet.getRetain());
            if (mqttVersion == null) {
                log.error("暂不支持该协议：protocol={}", mqttVersion);
                return null;
            }
            return MqttCodecUtil.decode(mqttVersion, packet.getMessage().getContent());
        }
        return null;
    }
}
