package com.ouyunc.im.processor;

import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.helper.MqttHelper;
import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 处理mqtt 客户端订阅的消息
 **/
public class MqttSubscribeMessageProcessor extends AbstractMessageProcessor {
    private static Logger log = LoggerFactory.getLogger(MqttSubscribeMessageProcessor.class);


    @Override
    public MessageEnum messageType() {
        return MessageEnum.MQTT_SUBSCRIBE;
    }


    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 真正处理逻辑的地方
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("MqttSubscribeMessageProcessor 正在处理客户端订阅信息...");
        MqttMessage mqttMessage = MqttHelper.unwrapPacket2Mqtt(packet);
        System.out.println(mqttMessage);
        // do nothing
    }
}
