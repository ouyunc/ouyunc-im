package com.ouyunc.message.processor.content;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.MessageContentType;
import com.ouyunc.base.constant.enums.MqttMessageContentTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.MqttLoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.message.processor.AbstractBaseProcessor;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * mqtt 断开连接
 *
 * 按 MQTT 3.1.1 规范：收到 DISCONNECT 报文表示客户端正常断连，
 * 此时 broker 不应发送遗嘱消息（will message），需清除遗嘱标记后再关闭 channel。
 */
public class MqttDisconnectMessageContentProcessor extends AbstractBaseProcessor<Integer> {
    private static final Logger log = LoggerFactory.getLogger(MqttDisconnectMessageContentProcessor.class);

    @Override
    public MessageContentType type() {
        return MqttMessageContentTypeEnum.MQTT_DISCONNECT;
    }


    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.warn("MqttDisconnectMessageProcessor 正在处理mqtt 正常断开连接消息...");
        // 正常 DISCONNECT：清除遗嘱标记，避免 channel close 事件触发遗嘱消息发送
        LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        if (loginClientInfo instanceof MqttLoginClientInfo mqttLoginClientInfo) {
            mqttLoginClientInfo.setEnableWill(NumberConstant.NUMBER_0);
        }
        ctx.close();
    }
}
