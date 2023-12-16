package com.ouyunc.im.processor;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.DeviceEnum;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.domain.MqttTopic;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.MqttHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.InnerExtraData;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.Target;
import com.ouyunc.im.utils.SystemClock;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        Message message = (Message) packet.getMessage();
        ExtraMessage extraMessage = JSON.parseObject(message.getExtra(), ExtraMessage.class);
        InnerExtraData innerExtraData = extraMessage.getInnerExtraData();
        String appKey = innerExtraData.getAppKey();
        String from = message.getFrom();
        MqttMessage mqttMessage = MqttHelper.unwrapPacket2Mqtt(packet);
        MqttMessageIdVariableHeader variableHeader = (MqttMessageIdVariableHeader) mqttMessage.variableHeader();
        MqttSubscribePayload payload = (MqttSubscribePayload) mqttMessage.payload();
        List<MqttTopicSubscription> mqttTopicSubscriptions = payload.topicSubscriptions();
        long timestamp = SystemClock.now();
        // 校验订阅的所有topic是否合法，只要有一个不合法都会丢弃并响应对应原因码
        if (!MqttHelper.validateTopic(mqttTopicSubscriptions)) {
            message.setContent(MqttHelper.gson().toJson(MqttMessageFactory.newMessage(
                    new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                    MqttMessageIdVariableHeader.from(variableHeader.messageId()),
                    new MqttSubAckPayload(IMConstant.MQTT_REASON_CODE_INVALID_TOPIC))));
            MessageHelper.sendMessage(packet.clone(), Target.newBuilder().targetIdentity(from).deviceEnum(DeviceEnum.getDeviceEnumByValue(packet.getDeviceType())).build());
        }
        // 查找topic是否存在，如果不存在则持久化并加入新的订阅客户端，如果存在则追加订阅信息
        List<Integer> qosReasonCode = new ArrayList<>();
        mqttTopicSubscriptions.forEach(mqttTopicSubscription -> {
            DbHelper.mqttTopicSubscribe(appKey ,from, mqttTopicSubscription,timestamp);
            qosReasonCode.add(mqttTopicSubscription.qualityOfService().value());
        });
        // 返回成功原因码
        message.setContent(MqttHelper.gson().toJson(MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(variableHeader.messageId()),
                new MqttSubAckPayload(qosReasonCode))));
        MessageHelper.sendMessage(packet.clone(), Target.newBuilder().targetIdentity(from).deviceEnum(DeviceEnum.getDeviceEnumByValue(packet.getDeviceType())).build());
        // 发布该topic 下的retain 消息

    }

}
