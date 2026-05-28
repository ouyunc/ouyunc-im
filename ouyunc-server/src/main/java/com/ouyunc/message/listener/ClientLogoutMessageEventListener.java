package com.ouyunc.message.listener;

import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.MqttLoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.MessageEventListener;
import com.ouyunc.core.listener.EventListener;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.domain.constants.YesOrNo;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.processor.AbstractBaseProcessor;
import com.ouyunc.message.processor.content.MqttPublishMessageContentProcessor;
import com.ouyunc.message.protocol.NativePacketProtocol;
import com.ouyunc.repository.DefaultRepository;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.mqtt.*;
import io.netty.util.CharsetUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

/**
 * @Author fzx
 * @Description: 离线监听器
 */
@EventListener(ring = EventRingEnum.CLIENT_LOGOUT)
class ClientLogoutMessageEventListener implements MessageEventListener<MessageEvent> {
    private static final Logger log = LoggerFactory.getLogger(ClientLogoutMessageEventListener.class);


    /**
     * @Author fzx
     * @Description 处理客户端离线事件，比如发送离线预警到mq
     */
    @Override
    public EventType type() {
        return MessageEventTypeEnum.CLIENT_LOGOUT;
    }

    @Override
    public void onEvent(MessageEvent event) {
        if (log.isDebugEnabled()) {
            log.warn("离线事件监听器正在处理：{}", event);
        }
        Object source = event.getSource();
        switch (source) {
            case MqttLoginClientInfo mqttLoginClientInfo -> {
                if (mqttLoginClientInfo.getEnableWill() != YesOrNo.YES.getCode()
                        || StringUtils.isBlank(mqttLoginClientInfo.getWillMessage())) {
                    break;
                }
                // 处理mqtt遗嘱信息
                MqttMessage willMqttMessage = MqttMessageFactory.newMessage(
                        new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.valueOf(mqttLoginClientInfo.getQos()), mqttLoginClientInfo.getIsWillRetain() == YesOrNo.YES.getCode(), NumberConstant.NUMBER_0),
                        new MqttPublishVariableHeader(mqttLoginClientInfo.getWillTopic(), NumberConstant.NUMBER_0), ByteBufAllocator.DEFAULT.buffer().writeBytes(mqttLoginClientInfo.getWillMessage().getBytes(CharsetUtil.UTF_8)));
                // 发送遗嘱消息到willTopic
                AbstractBaseProcessor<? extends Number> baseProcessor = MessageServerContext.messageContentProcessorCache.get(MqttMessageContentTypeEnum.MQTT_PUBLISH.getType());
                if (baseProcessor instanceof MqttPublishMessageContentProcessor mqttPublishMessageContentProcessor) {
                    mqttPublishMessageContentProcessor.doPublishMessage(willMqttMessage);
                }
            }
            case LoginClientInfo loginClientInfo -> {
                String identity = loginClientInfo.getIdentity();
                String appKey = loginClientInfo.getAppKey();
                if (loginClientInfo.getEnableWill() != YesOrNo.YES.getCode()) {
                    break;
                }
                Collection<String> friendIds = DefaultRepository.INSTANCE.getFriendIds(appKey, identity);
                for (String friendId : friendIds) {
                    List<LoginClientInfo> loginClientInfos = ClientHelper.onlineAll(appKey, friendId);
                    if (CollectionUtils.isNotEmpty(loginClientInfos)) {
                        Metadata metadata = new Metadata();
                        metadata.setAppKey(appKey);
                        Message message = new Message(MessageContext.idGenerator().generateIdStr(), identity, friendId, MessageContentTypeEnum.TEXT_CONTENT.getType(), loginClientInfo.getWillMessage(), TimeUtil.currentTimeMillis(), metadata);
                        Packet packet = new Packet(NativePacketProtocol.OUYUNC.getProtocol(), NativePacketProtocol.OUYUNC.getProtocolVersion(), MessageContext.idGenerator().generateId(), DeviceTypeEnum.PC.getType(), NetworkEnum.OTHER.getValue(), Encrypt.SymmetryEncrypt.NONE.getValue(), Serializer.PROTO_STUFF.getValue(), MessageTypeEnum.CLIENT_LOGOUT.getType(), message);
                        MessageHelper.asyncSendMessage(packet, loginClientInfos);
                    }
                }
            }
            case null, default -> {
            }
        }
        // do other something
    }
}
