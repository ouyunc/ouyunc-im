package com.ouyunc.message.processor;


import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MqttMessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.MqttCodecUtil;
import com.ouyunc.base.utils.MqttDecoderUtil;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;

/**
 * @Author fzx
 * @Description: Mqtt 消息处理器
 **/
public class MqttMessageProcessor extends AbstractMessageProcessor<Byte> {


    private static final Logger log = LoggerFactory.getLogger(MqttMessageProcessor.class);



    @Override
    public MessageType type() {
        return MqttMessageTypeEnum.MQTT;
    }

    /***
     * @author fzx
     * @description 消息前置处理，做登录业务逻辑
     */
    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("正在预处理mqtt消息...");
        Message message = packet.getMessage();
        MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(packet.getRetain());
        if (mqttVersion == null) {
            log.error("无法识别的mqtt协议版本: {}", packet.getRetain());
        }
        MqttMessage mqttMessage = MqttCodecUtil.decode(mqttVersion, message.getContent());
        System.out.println(mqttMessage);

    }

    /***
     * @author fzx
     * @description 业务处理，登录消息不需要做任何处理
     */
    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        // do nothing
    }

    @Override
    public void postProcess(ChannelHandlerContext ctx, Packet packet) {

    }
}
