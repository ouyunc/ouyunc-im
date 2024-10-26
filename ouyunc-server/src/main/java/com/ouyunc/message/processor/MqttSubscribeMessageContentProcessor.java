package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.MessageContentType;
import com.ouyunc.base.constant.enums.MqttMessageContentTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.repository.MqttRepository;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * mqtt 订阅
 */
public class MqttSubscribeMessageContentProcessor extends AbstractBaseProcessor<Integer>{
    private static final Logger log = LoggerFactory.getLogger(MqttSubscribeMessageContentProcessor.class);

    @Override
    public MessageContentType type() {
        return MqttMessageContentTypeEnum.MQTT_SUBSCRIBE;
    }
    @SuppressWarnings("unchecked")
    @Override
    public MqttRepository repository() {
        return new MqttRepository();
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("MqttSubscribeMessageContentProcessor 正在处理外部客户端订阅 {} ...", packet);
        }


    }
}
