package com.ouyunc.message.processor.content;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MessageContentType;
import com.ouyunc.base.constant.enums.MqttMessageContentTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.processor.AbstractBaseProcessor;
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
public class MqttPingPongMessageContentProcessor extends AbstractBaseProcessor<Integer> {
    private static final Logger log = LoggerFactory.getLogger(MqttPingPongMessageContentProcessor.class);

    @Override
    public MessageContentType type() {
        return MqttMessageContentTypeEnum.MQTT_PINGREQ;
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("MqttPingPongMessageProcessor 正在处理外部客户端心跳 {} ...", packet);
        }
        // 安全校验：未认证连接不响应 PINGRESP，防止未登录客户端通过心跳无限保活
        LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        if (loginClientInfo == null) {
            log.warn("未认证连接发送 PINGREQ，关闭连接: {}", ctx.channel().remoteAddress());
            ctx.close();
            return;
        }
        // 清除心跳超时次数
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_READ_TIMEOUT_TIMES, null);
        final MqttMessage mqttPongMessage = new MqttMessage(new MqttFixedHeader(MqttMessageType.PINGRESP, false,
                MqttQoS.AT_MOST_ONCE, false, 0));
        if (ctx.channel().eventLoop().inEventLoop()) {
            MessageHelper.tryWriteObject(ctx.channel(), mqttPongMessage, packet, sendResult -> {});
        } else {
            ctx.channel().eventLoop().execute(() -> MessageHelper.tryWriteObject(ctx.channel(), mqttPongMessage, packet, sendResult -> {}));
        }
    }
}
