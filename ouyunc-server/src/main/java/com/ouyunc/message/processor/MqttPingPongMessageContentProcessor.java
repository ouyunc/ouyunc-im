package com.ouyunc.message.processor;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MessageContentType;
import com.ouyunc.base.constant.enums.MqttMessageContentTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.ChannelAttrUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * mqtt 心跳
 */
public class MqttPingPongMessageContentProcessor extends AbstractBaseProcessor<Integer>{
    private static final Logger log = LoggerFactory.getLogger(MqttPingPongMessageContentProcessor.class);

    @Override
    public MessageContentType type() {
        return MqttMessageContentTypeEnum.MQTT_CONNECT_PINGREQ;
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("MqttPingPongMessageProcessor 正在处理外部客户端心跳 {} ...", packet);
        }
        // 可能在三次之内再次发起心跳，此时需要清除 之前心跳超时次数的历史记录
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_READ_TIMEOUT_TIMES, null);
        final MqttMessage mqttPongMessage = new MqttMessage(new MqttFixedHeader(MqttMessageType.PINGRESP, false,
                MqttQoS.AT_MOST_ONCE, false, 0));
        // 发送pong
        ctx.writeAndFlush(mqttPongMessage);
    }
}
