package com.ouyunc.message.processor.content;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.DeviceTypeEnum;
import com.ouyunc.base.constant.enums.MessageContentType;
import com.ouyunc.base.constant.enums.MqttMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MqttMessageTypeEnum;
import com.ouyunc.base.constant.enums.NetworkEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.MqttCodecUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.processor.AbstractBaseBiProcessor;
import com.ouyunc.message.protocol.NativePacketProtocol;
import com.ouyunc.repository.MqttRepository;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * MQTT PUBLISH：按 Redis 订阅表匹配 topic filter，扇出到在线客户端（跳过发布者）。
 */
public class MqttPublishMessageContentBiProcessor extends AbstractBaseBiProcessor<Integer> {
    private static final Logger log = LoggerFactory.getLogger(MqttPublishMessageContentBiProcessor.class);

    @Override
    public MessageContentType type() {
        return MqttMessageContentTypeEnum.MQTT_PUBLISH;
    }

    @SuppressWarnings("unchecked")
    @Override
    public MqttRepository repository() {
        return MqttRepository.INSTANCE;
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (log.isDebugEnabled()) {
            log.debug("MqttPublishMessageContentProcessor 正在处理mqtt 发布消息 {} ...", packet);
        }
        MqttMessage mqttPublishMessage = MqttCodecUtil.decode(MqttCodecUtil.getMqttVersion(packet.getRetain()), packet.getMessage().getContent());
        LoginClientInfo publisher = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        String appKey = packet.getMessage().getMetadata() != null ? packet.getMessage().getMetadata().getAppKey() : null;
        if (StringUtils.isBlank(appKey) && publisher != null) {
            appKey = publisher.getAppKey();
        }
        String publisherCombo = publisher == null ? null : IdentityUtil.generalComboIdentity(
                publisher.getAppKey(), publisher.getIdentity(), publisher.getDeviceType());
        doPublishMessage(mqttPublishMessage, appKey, publisherCombo, packet);
        qosPostHandle(ctx, packet);
    }

    /**
     * 遗嘱等无入站 Packet 的发布入口。
     */
    public void doPublishMessage(MqttMessage mqttMessage) {
        doPublishMessage(mqttMessage, null, null, null);
    }

    /**
     * 发布并扇出。
     *
     * @param appKey          订阅表命名空间
     * @param publisherCombo  发布者 comboIdentity，扇出时跳过
     * @param sourcePacket    入站包（优先复用）；遗嘱可为 null
     */
    public void doPublishMessage(MqttMessage mqttMessage, String appKey, String publisherCombo, Packet sourcePacket) {
        if (!(mqttMessage instanceof MqttPublishMessage mqttPublishMessage)) {
            return;
        }
        String topicName = mqttPublishMessage.variableHeader().topicName();
        if (StringUtils.isBlank(appKey) && StringUtils.isNotBlank(publisherCombo)) {
            appKey = IdentityUtil.revertAppKey(publisherCombo);
        }
        repository().savePublishMessage(appKey, mqttMessage);
        if (StringUtils.isBlank(appKey) || StringUtils.isBlank(topicName)) {
            log.warn("MQTT 发布缺少 appKey 或 topic，跳过扇出 topic={}", topicName);
            return;
        }
        List<MqttRepository.MqttSubscriber> subscribers = repository().findPublishSubscribers(appKey, topicName);
        if (CollectionUtils.isEmpty(subscribers)) {
            log.debug("MQTT 发布无订阅者 appKey={} topic={}", appKey, topicName);
            return;
        }
        List<LoginClientInfo> clients = new ArrayList<>();
        for (MqttRepository.MqttSubscriber subscriber : subscribers) {
            if (StringUtils.isNotBlank(publisherCombo) && publisherCombo.equals(subscriber.comboIdentity())) {
                continue;
            }
            LoginClientInfo client = resolveSubscriber(subscriber.comboIdentity());
            if (client != null) {
                clients.add(client);
            }
        }
        if (clients.isEmpty()) {
            return;
        }
        Packet fanoutPacket = sourcePacket != null ? sourcePacket : wrapPublishPacket(mqttPublishMessage, appKey, publisherCombo);
        if (fanoutPacket == null) {
            log.warn("MQTT 扇出无法构造 Packet，topic={}", topicName);
            return;
        }
        int packetId = mqttPublishMessage.variableHeader().packetId();
        String encoded = fanoutPacket.getMessage() == null ? null : fanoutPacket.getMessage().getContent();
        if (packetId > 0 && StringUtils.isNotBlank(encoded)) {
            for (LoginClientInfo client : clients) {
                String combo = IdentityUtil.generalComboIdentity(client.getAppKey(), client.getIdentity(), client.getDeviceType());
                repository().saveInflight(client.getAppKey(), combo, packetId, encoded);
            }
        }
        MessageHelper.asyncSendMessage(fanoutPacket, clients);
    }

    private static LoginClientInfo resolveSubscriber(String comboIdentity) {
        if (StringUtils.isBlank(comboIdentity)) {
            return null;
        }
        ChannelHandlerContext ctx = MessageServerContext.localLoginClientRegisterTable.get(comboIdentity);
        if (ctx != null) {
            LoginClientInfo local = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
            if (local != null) {
                return local;
            }
        }
        try {
            String appKey = IdentityUtil.revertAppKey(comboIdentity);
            return MessageServerContext.remoteLoginClientInfoCache.get(
                    CacheConstant.buildLoginCacheKey(appKey, comboIdentity));
        } catch (Exception e) {
            log.warn("MQTT 订阅者 combo 无法解析: {}", comboIdentity, e);
            return null;
        }
    }

    private static Packet wrapPublishPacket(MqttPublishMessage mqttPublishMessage, String appKey, String publisherCombo) {
        MqttVersion mqttVersion = MqttVersion.MQTT_3_1_1;
        String content = MqttCodecUtil.encode(mqttVersion, mqttPublishMessage);
        Metadata metadata = new Metadata();
        metadata.setAppKey(appKey);
        metadata.setServerTime(TimeUtil.currentTimeMillis());
        String from = StringUtils.isBlank(publisherCombo) ? "" : IdentityUtil.revertIdentity(publisherCombo);
        Message message = new Message(
                MessageContext.idGenerator().generateIdStr(),
                from,
                MessageServerContext.serverProperties().getLocalServerAddress(),
                MqttMessageContentTypeEnum.MQTT_PUBLISH.getType(),
                content,
                mqttPublishMessage.fixedHeader().qosLevel().value(),
                TimeUtil.currentTimeMillis(),
                metadata);
        return new Packet(
                NativePacketProtocol.MQTT.getProtocol(),
                NativePacketProtocol.MQTT.getProtocolVersion(),
                MessageContext.idGenerator().generateId(),
                DeviceTypeEnum.M.getType(),
                NetworkEnum.OTHER.getValue(),
                Encrypt.SymmetryEncrypt.NONE.getValue(),
                Serializer.PROTO_STUFF.getValue(),
                MqttMessageTypeEnum.MQTT.getType(),
                mqttVersion.protocolLevel(),
                message);
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
