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
import com.ouyunc.base.constant.enums.YesOrNo;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.processor.AbstractBaseBiProcessor;
import com.ouyunc.message.processor.content.MqttPublishMessageContentBiProcessor;
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
        long consumeLagMs = Math.max(0L, TimeUtil.currentTimeMillis() - event.getPublishTime());
        Object source = event.getSource();
        switch (source) {
            case MqttLoginClientInfo mqttLoginClientInfo -> handleMqttLogout(event, mqttLoginClientInfo, consumeLagMs);
            case LoginClientInfo loginClientInfo -> handleNativeLogout(event, loginClientInfo, consumeLagMs);
            case null -> log.warn("[客户端登出] source 为空, eventId={}, publishTime={}, consumeLagMs={}",
                    event.getId(), event.getPublishTime(), consumeLagMs);
            default -> log.warn("[客户端登出] source 类型不支持, eventId={}, publishTime={}, consumeLagMs={}, sourceType={}",
                    event.getId(), event.getPublishTime(), consumeLagMs, source.getClass().getName());
        }
    }

    private void handleMqttLogout(MessageEvent event, MqttLoginClientInfo mqttLoginClientInfo, long consumeLagMs) {
        if (mqttLoginClientInfo.getEnableWill() != YesOrNo.YES.getCode()
                || StringUtils.isBlank(mqttLoginClientInfo.getWillMessage())) {
            log.info("[客户端登出] MQTT 已接收(未开启遗嘱), eventId={}, appKey={}, identity={}, deviceType={}, loginServer={}, consumeLagMs={}",
                    event.getId(), mqttLoginClientInfo.getAppKey(), mqttLoginClientInfo.getIdentity(),
                    mqttLoginClientInfo.getDeviceType(), mqttLoginClientInfo.getLoginServerAddress(), consumeLagMs);
            return;
        }
        MqttMessage willMqttMessage = MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.valueOf(mqttLoginClientInfo.getQos()), mqttLoginClientInfo.getIsWillRetain() == YesOrNo.YES.getCode(), NumberConstant.NUMBER_0),
                new MqttPublishVariableHeader(mqttLoginClientInfo.getWillTopic(), NumberConstant.NUMBER_0), ByteBufAllocator.DEFAULT.buffer().writeBytes(mqttLoginClientInfo.getWillMessage().getBytes(CharsetUtil.UTF_8)));
        AbstractBaseBiProcessor<? extends Number> baseProcessor = MessageServerContext.messageContentProcessorCache.get(MqttMessageContentTypeEnum.MQTT_PUBLISH.getType());
        if (baseProcessor instanceof MqttPublishMessageContentBiProcessor mqttPublishMessageContentProcessor) {
            mqttPublishMessageContentProcessor.doPublishMessage(willMqttMessage);
            log.info("[客户端登出] MQTT 遗嘱已发布, eventId={}, appKey={}, identity={}, deviceType={}, willTopic={}, qos={}, loginServer={}, consumeLagMs={}",
                    event.getId(), mqttLoginClientInfo.getAppKey(), mqttLoginClientInfo.getIdentity(),
                    mqttLoginClientInfo.getDeviceType(), mqttLoginClientInfo.getWillTopic(), mqttLoginClientInfo.getQos(),
                    mqttLoginClientInfo.getLoginServerAddress(), consumeLagMs);
        } else {
            log.warn("[客户端登出] MQTT 遗嘱发布失败(处理器缺失), eventId={}, appKey={}, identity={}, willTopic={}, consumeLagMs={}",
                    event.getId(), mqttLoginClientInfo.getAppKey(), mqttLoginClientInfo.getIdentity(),
                    mqttLoginClientInfo.getWillTopic(), consumeLagMs);
        }
    }

    private void handleNativeLogout(MessageEvent event, LoginClientInfo loginClientInfo, long consumeLagMs) {
        String identity = loginClientInfo.getIdentity();
        String appKey = loginClientInfo.getAppKey();
        if (loginClientInfo.getEnableWill() != YesOrNo.YES.getCode()) {
            log.info("[客户端登出] 已接收(未开启下线通知), eventId={}, appKey={}, identity={}, deviceType={}, loginServer={}, consumeLagMs={}",
                    event.getId(), appKey, identity, loginClientInfo.getDeviceType(),
                    loginClientInfo.getLoginServerAddress(), consumeLagMs);
            return;
        }
        Collection<String> friendIds = DefaultRepository.INSTANCE.getFriendIds(appKey, identity);
        int notifiedFriends = 0;
        int notifiedSessions = 0;
        for (String friendId : friendIds) {
            List<LoginClientInfo> loginClientInfos = ClientHelper.onlineAll(appKey, friendId);
            if (CollectionUtils.isNotEmpty(loginClientInfos)) {
                notifiedFriends++;
                notifiedSessions += loginClientInfos.size();
                Metadata metadata = new Metadata();
                metadata.setAppKey(appKey);
                Message message = new Message(MessageContext.idGenerator().generateIdStr(), identity, friendId, MessageContentTypeEnum.TEXT_CONTENT.getType(), loginClientInfo.getWillMessage(), TimeUtil.currentTimeMillis(), metadata);
                Packet packet = new Packet(NativePacketProtocol.OUYUNC.getProtocol(), NativePacketProtocol.OUYUNC.getProtocolVersion(), MessageContext.idGenerator().generateId(), DeviceTypeEnum.PC.getType(), NetworkEnum.OTHER.getValue(), Encrypt.SymmetryEncrypt.NONE.getValue(), Serializer.PROTO_STUFF.getValue(), MessageTypeEnum.CLIENT_LOGOUT.getType(), message);
                MessageHelper.asyncSendMessage(packet, loginClientInfos);
            }
        }
        log.info("[客户端登出] 已处理, eventId={}, appKey={}, identity={}, deviceType={}, loginServer={}, friendCount={}, notifiedFriends={}, notifiedSessions={}, consumeLagMs={}",
                event.getId(), appKey, identity, loginClientInfo.getDeviceType(),
                loginClientInfo.getLoginServerAddress(), friendIds.size(), notifiedFriends, notifiedSessions, consumeLagMs);
    }
}
