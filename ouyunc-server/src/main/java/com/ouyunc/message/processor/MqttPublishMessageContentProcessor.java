package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.MessageContentType;
import com.ouyunc.base.constant.enums.MqttMessageContentTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.MqttCodecUtil;
import com.ouyunc.repository.MqttRepository;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * mqtt 发布mqtt消息
 */
public class MqttPublishMessageContentProcessor extends AbstractBaseProcessor<Integer>{
    private static final Logger log = LoggerFactory.getLogger(MqttPublishMessageContentProcessor.class);

    @Override
    public MessageContentType type() {
        return MqttMessageContentTypeEnum.MQTT_PUBLISH;
    }
    @SuppressWarnings("unchecked")
    @Override
    public MqttRepository repository() {
        return MqttRepository.INSTANCE;
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("MqttPublishMessageContentProcessor 正在处理mqtt 发布消息 {} ...", packet);
        }
        MqttMessage mqttPublishMessage = MqttCodecUtil.decode(MqttCodecUtil.getMqttVersion(packet.getRetain()), packet.getMessage().getContent());
        // 发送消息
        doPublishMessage(mqttPublishMessage);
        // 处理qos
        qosPostHandle(ctx, packet);
    }

    /**
     * 发布消息
     * @param mqttMessage
     */
    public void doPublishMessage(MqttMessage mqttMessage){
        // 存储数据
        repository().savePublishMessage(mqttMessage);
        if (mqttMessage instanceof MqttPublishMessage mqttPublishMessage) {
            // TODO 1，找到所有的订阅的客户端，发送消息；
            log.info("发布消息：{}", mqttPublishMessage);
        }
    }

    @Override
    public boolean qosPreHandle(ChannelHandlerContext ctx, Packet packet) {
        return super.qosPreHandle(ctx, packet);
    }

    @Override
    public void qosPostHandle(ChannelHandlerContext ctx, Packet packet) {
        super.qosPostHandle(ctx, packet);
    }
}
