package com.ouyunc.message.processor;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MessageContentType;
import com.ouyunc.base.constant.enums.MqttMessageContentTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.MqttCodecUtil;
import com.ouyunc.repository.MqttRepository;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * mqtt 取消订阅
 */
public class MqttUnSubscribeMessageContentProcessor extends AbstractBaseProcessor<Integer>{
    private static final Logger log = LoggerFactory.getLogger(MqttUnSubscribeMessageContentProcessor.class);

    @Override
    public MessageContentType type() {
        return MqttMessageContentTypeEnum.MQTT_UNSUBSCRIBE;
    }
    @SuppressWarnings("unchecked")
    @Override
    public MqttRepository repository() {
        return new MqttRepository();
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("MqttUnSubscribeMessageContentProcessor 正在处理外部客户端取消订阅 {} ...", packet);
        }
        Message message = packet.getMessage();
        MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(packet.getRetain());
        MqttMessage mqttMessage = MqttCodecUtil.decode(mqttVersion, message.getContent());
        if (mqttMessage instanceof MqttUnsubscribeMessage mqttUnsubscribeMessage) {
            List<String> topicFilters = mqttUnsubscribeMessage.payload().topics();
            LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
            if (loginClientInfo == null) {
                log.error("MqttUnSubscribeMessageContentProcessor 登录信息不存在！正在关闭该channel");
                ctx.close();
                return;
            }
            String comboIdentity = IdentityUtil.generalComboIdentity(loginClientInfo.getAppKey(), loginClientInfo.getIdentity(), loginClientInfo.getDeviceType());
            topicFilters.forEach(topicFilter -> {
                repository().unSubscribe(topicFilter, comboIdentity);
                log.debug("UNSUBSCRIBE - clientId: {}, topicFilter: {}", comboIdentity, topicFilter);
            });
            MqttUnsubAckMessage unsubAckMessage = (MqttUnsubAckMessage) MqttMessageFactory.newMessage(
                    new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                    MqttMessageIdVariableHeader.from(mqttUnsubscribeMessage.variableHeader().messageId()), null);
            ctx.writeAndFlush(unsubAckMessage);
        }else {
            log.error("MqttUnSubscribeMessageContentProcessor 非法取消订阅主题！");
        }

    }
}
