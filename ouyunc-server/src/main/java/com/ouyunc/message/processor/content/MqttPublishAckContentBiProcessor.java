package com.ouyunc.message.processor.content;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MessageContentType;
import com.ouyunc.base.constant.enums.MqttMessageContentTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.MqttCodecUtil;
import com.ouyunc.message.processor.AbstractBaseBiProcessor;
import com.ouyunc.repository.MqttRepository;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * mqtt 接收客户端的 qos1 发布确认信息
 */
public class MqttPublishAckContentBiProcessor extends AbstractBaseBiProcessor<Integer> {
    private static final Logger log = LoggerFactory.getLogger(MqttPublishAckContentBiProcessor.class);

    @Override
    public MessageContentType type() {
        return MqttMessageContentTypeEnum.MQTT_PUBACK;
    }

    @SuppressWarnings("unchecked")
    @Override
    public MqttRepository repository() {
        return MqttRepository.INSTANCE;
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("MqttPublishAckContentProcessor 正在处理mqtt PUBACK {} ...", packet);
        }
        LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        if (loginClientInfo == null) {
            log.warn("MQTT PUBACK 时登录信息不存在，关闭 channel");
            ctx.close();
            return;
        }
        MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(packet.getRetain());
        MqttMessage mqttMessage = MqttCodecUtil.decode(mqttVersion, packet.getMessage().getContent());
        if (!(mqttMessage instanceof MqttPubAckMessage pubAck)) {
            log.warn("MQTT PUBACK 解码失败 packetId={}", packet.getPacketId());
            return;
        }
        int messageId = pubAck.variableHeader().messageId();
        String comboIdentity = IdentityUtil.generalComboIdentity(
                loginClientInfo.getAppKey(), loginClientInfo.getIdentity(), loginClientInfo.getDeviceType());
        repository().removeInflight(loginClientInfo.getAppKey(), comboIdentity, messageId);
    }
}
