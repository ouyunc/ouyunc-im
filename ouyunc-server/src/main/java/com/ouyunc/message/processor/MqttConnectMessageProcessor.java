package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.MessageContentType;
import com.ouyunc.base.constant.enums.MqttMessageContentTypeEnum;
import com.ouyunc.base.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * mqtt connect
 */
public class MqttConnectMessageProcessor extends AbstractBaseProcessor<Integer>{
    private static final Logger log = LoggerFactory.getLogger(MqttConnectMessageProcessor.class);

    @Override
    public MessageContentType type() {
        return MqttMessageContentTypeEnum.MQTT_CONNECT;
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.info("MqttConnectMessageProcessor connect 正在处理mqtt 连接消息...");
    }
}
