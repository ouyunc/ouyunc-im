package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.MessageContentType;
import com.ouyunc.base.constant.enums.MqttMessageContentTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.MqttCodecUtil;
import com.ouyunc.repository.MqttRepository;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
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
        return new MqttRepository();
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("MqttPublishMessageContentProcessor 正在处理mqtt 发布消息 {} ...", packet);
        }
        String content = packet.getMessage().getContent();
        MqttPublishMessage decode = (MqttPublishMessage) MqttCodecUtil.decode(MqttCodecUtil.getMqttVersion(packet.getRetain()), content);
        log.error("################==   ",decode.variableHeader().packetId());
        System.out.println("**********==" + decode.variableHeader().packetId());
        byte[] messageBytes = new byte[decode.payload().readableBytes()];
        decode.payload().getBytes(decode.payload().readerIndex(), messageBytes);
        MqttPublishMessage publishMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.PUBLISH, true, MqttQoS.AT_MOST_ONCE, true, 0),
                new MqttPublishVariableHeader(decode.variableHeader().topicName(), 0), Unpooled.buffer().writeBytes(messageBytes));
        ctx.writeAndFlush(publishMessage);
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
