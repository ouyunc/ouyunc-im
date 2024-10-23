package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.MessageContentType;
import com.ouyunc.base.constant.enums.MqttMessageContentTypeEnum;
import com.ouyunc.base.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * mqtt 断开连接
 */
public class MqttDisconnectMessageContentProcessor extends AbstractBaseProcessor<Integer>{
    private static final Logger log = LoggerFactory.getLogger(MqttDisconnectMessageContentProcessor.class);

    @Override
    public MessageContentType type() {
        return MqttMessageContentTypeEnum.MQTT_DISCONNECT;
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.warn("MqttDisconnectMessageProcessor 正在处理mqtt 断开连接消息...");
        ctx.close();
    }
}
