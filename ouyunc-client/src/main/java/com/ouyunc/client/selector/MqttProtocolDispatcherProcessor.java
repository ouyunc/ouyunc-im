package com.ouyunc.client.selector;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;
import com.ouyunc.base.model.Protocol;
import com.ouyunc.client.handler.MqttProtocolHandler;
import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fzx
 * @description mqtt 协议
 */
public class MqttProtocolDispatcherProcessor implements ProtocolSelector<Protocol, Channel> {
    private static final Logger log = LoggerFactory.getLogger(MqttProtocolDispatcherProcessor.class);

    @Override
    public boolean match(Protocol protocol) {
        return ProtocolTypeEnum.MQTT.getProtocol() == protocol.getProtocol() && ProtocolTypeEnum.MQTT.getProtocolVersion() == protocol.getProtocolVersion();
    }

    @Override
    public void process(Channel channel) {
        channel.pipeline()
                .addLast(MessageConstant.MQTT_DECODER_HANDLER, new MqttDecoder())
                .addLast(MessageConstant.MQTT_ENCODER_HANDLER, MqttEncoder.INSTANCE)
                .addLast(MessageConstant.MQTT_SERVER_HANDLER, new MqttProtocolHandler());
    }


}
