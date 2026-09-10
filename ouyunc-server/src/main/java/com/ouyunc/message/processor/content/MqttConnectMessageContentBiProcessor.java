package com.ouyunc.message.processor.content;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.DeviceTypeEnum;
import com.ouyunc.base.constant.enums.LoginScopeEnum;
import com.ouyunc.base.constant.enums.MessageContentType;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.MqttMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MqttMessageTypeEnum;
import com.ouyunc.base.constant.enums.NetworkEnum;
import com.ouyunc.base.constant.enums.OnlineEnum;
import com.ouyunc.base.constant.enums.YesOrNo;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.MqttLoginClientInfo;
import com.ouyunc.base.model.Protocol;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.packet.message.content.ServerNotifyContent;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.MqttCodecUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.handler.HeartBeatHandler;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.LoginSessionDirectory;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.processor.AbstractBaseBiProcessor;
import com.ouyunc.message.protocol.NativePacketProtocol;
import com.ouyunc.message.validator.AppKeyValidator;
import com.ouyunc.message.validator.LoginAuthValidator;
import com.ouyunc.repository.MqttRepository;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttConnAckVariableHeader;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectPayload;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttConnectVariableHeader;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttIdentifierRejectedException;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageFactory;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttUnacceptableProtocolVersionException;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * mqtt connect。username=appKey，password={@code createTime#signature}，与原生登录同一套 MD5 签名。
 */
public class MqttConnectMessageContentBiProcessor extends AbstractBaseBiProcessor<Integer> {
    private static final Logger log = LoggerFactory.getLogger(MqttConnectMessageContentBiProcessor.class);

    @Override
    public MessageContentType type() {
        return MqttMessageContentTypeEnum.MQTT_CONNECT;
    }

    @SuppressWarnings("unchecked")
    @Override
    public MqttRepository repository() {
        return MqttRepository.INSTANCE;
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        long loginTimestamp = TimeUtil.currentTimeMillis();
        Message connectMessage = packet.getMessage();
        MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(packet.getRetain());
        if (mqttVersion == null) {
            log.error("MqttConnectMessageProcessor connect 消息解码失败，请检查协议版本是否正确！");
            return;
        }
        if (!MessageServerContext.isAcceptingNewConnections()) {
            log.warn("MQTT CONNECT 被拒绝：服务摘流中, channel={}", ctx.channel().id().asShortText());
            refuse(ctx, packet, MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
            return;
        }
        if (!MessageServerContext.serverProperties().isMqttEnabled()) {
            log.warn("MQTT CONNECT 被拒绝：mqtt.enabled=false, channel={}", ctx.channel().id().asShortText());
            refuse(ctx, packet, MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
            return;
        }
        MqttMessage mqttMessage = MqttCodecUtil.decode(mqttVersion, connectMessage.getContent());
        if (!(mqttMessage instanceof MqttConnectMessage mqttConnectMessage)) {
            log.error("mqtt 非法连接connect 消息！");
            return;
        }
        if (mqttMessage.decoderResult().isFailure()) {
            refuseDecoderFailure(ctx, packet, mqttMessage.decoderResult().cause());
            return;
        }
        MqttConnectPayload mqttConnectPayload = mqttConnectMessage.payload();
        if (StringUtils.isBlank(mqttConnectPayload.clientIdentifier())) {
            refuse(ctx, packet, MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED);
            return;
        }
        MqttConnectVariableHeader mqttConnectVariableHeader = mqttConnectMessage.variableHeader();
        String appKey = mqttConnectPayload.userName();
        byte[] passwordBytes = mqttConnectPayload.passwordInBytes();
        String password = passwordBytes == null ? null : new String(passwordBytes, CharsetUtil.UTF_8);
        LoginAuthValidator.MqttPassword mqttPassword = LoginAuthValidator.parseMqttPassword(password);
        if (mqttPassword == null) {
            refuse(ctx, packet, MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
            return;
        }
        MqttLoginClientInfo mqttLoginClientInfo = buildMqttLogin(ctx, mqttConnectPayload, mqttConnectVariableHeader,
                mqttVersion, appKey, mqttPassword, loginTimestamp);
        if (mqttLoginClientInfo == null || !authenticate(ctx, mqttLoginClientInfo)) {
            refuse(ctx, packet, MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
            return;
        }
        String comboIdentity = IdentityUtil.generalComboIdentity(
                mqttLoginClientInfo.getAppKey(), mqttLoginClientInfo.getIdentity(), DeviceTypeEnum.M.getType());
        LoginClientInfo cacheLoginClientInfo = MessageServerContext.remoteLoginClientInfoCache.get(
                CacheConstant.buildLoginCacheKey(mqttLoginClientInfo.getAppKey(), comboIdentity));
        ChannelHandlerContext bindCtx = MessageServerContext.localLoginClientRegisterTable.get(comboIdentity);
        kickPreviousMqttSession(packet, mqttLoginClientInfo, cacheLoginClientInfo, bindCtx, loginTimestamp);
        boolean sessionPresent = cacheLoginClientInfo != null && !mqttConnectMessage.variableHeader().isCleanSession();
        installCloseHook(ctx, comboIdentity, mqttLoginClientInfo);
        ClientHelper.bindAsync(ctx, mqttLoginClientInfo).whenComplete((unused, ex) ->
                ctx.executor().execute(() ->
                        completeMqttConnectAfterRemoteBind(ctx, packet, mqttConnectMessage, sessionPresent, mqttLoginClientInfo, ex)));
    }

    private MqttLoginClientInfo buildMqttLogin(ChannelHandlerContext ctx, MqttConnectPayload payload,
                                               MqttConnectVariableHeader header, MqttVersion mqttVersion,
                                               String appKey, LoginAuthValidator.MqttPassword mqttPassword,
                                               long loginTimestamp) {
        Protocol protocol = ctx.channel().attr(NativePacketProtocol.protocolAttrKey).get();
        if (protocol == null) {
            log.warn("Protocol not set on channel, closing MQTT connection");
            ctx.close();
            return null;
        }
        byte[] willMessageInBytes = payload.willMessageInBytes();
        String willMessage = willMessageInBytes == null ? null : new String(willMessageInBytes, CharsetUtil.UTF_8);
        int sessionExpiryInterval = NumberConstant.NUMBER_NEGATIVE_1;
        MqttProperties.MqttProperty<Integer> sessionExpiryIntervalProperty =
                header.properties().getProperty(MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL.value());
        if (sessionExpiryIntervalProperty != null) {
            sessionExpiryInterval = sessionExpiryIntervalProperty.value();
        }
        return new MqttLoginClientInfo(
                protocol.getProtocol(), protocol.getProtocolVersion(),
                MessageContext.messageProperties.getLocalServerAddress(), OnlineEnum.ONLINE, null,
                ClientHelper.calculateClientHeartBeatTimeout(header.keepAliveTimeSeconds()), loginTimestamp,
                appKey, payload.clientIdentifier(), DeviceTypeEnum.M.getType(), null, payload.clientIdentifier(),
                mqttPassword.signature(), Encrypt.AsymmetricEncrypt.MD5.getValue(), header.keepAliveTimeSeconds(),
                mqttPassword.createTime(),
                header.isWillFlag() ? NumberConstant.NUMBER_1 : NumberConstant.NUMBER_0,
                header.willQos(), mqttVersion.protocolLevel(),
                header.isWillRetain() ? NumberConstant.NUMBER_1 : NumberConstant.NUMBER_0,
                willMessage, payload.willTopic(),
                header.isCleanSession() ? NumberConstant.NUMBER_1 : NumberConstant.NUMBER_0,
                sessionExpiryInterval, YesOrNo.NO.getCode(), null);
    }

    /**
     * appKey 配额、设备白名单含 MQTT 所用 {@link DeviceTypeEnum#M}、签名与客服建档。
     */
    private boolean authenticate(ChannelHandlerContext ctx, MqttLoginClientInfo loginClientInfo) {
        loginClientInfo.setScope(LoginScopeEnum.NORMAL.getType());
        if (AppKeyValidator.INSTANCE.negate().verify(loginClientInfo.getAppKey(), ctx)) {
            return false;
        }
        if (!MessageServerContext.deviceTypeList(loginClientInfo.getAppKey(), loginClientInfo.getIdentity())
                .contains(DeviceTypeEnum.M.getType())) {
            log.warn("MQTT 登录拒绝：appKey={} 未开通设备类型 M", loginClientInfo.getAppKey());
            return false;
        }
        return LoginAuthValidator.verify(loginClientInfo);
    }

    /**
     * 同 clientId 顶号：本机关旧连接；跨节点发 DISCONNECT / 远程登录通知。不因 sn 相同跳过。
     */
    private void kickPreviousMqttSession(Packet packet, MqttLoginClientInfo mqttLogin,
                                         LoginClientInfo cacheLoginClientInfo, ChannelHandlerContext bindCtx,
                                         long loginTimestamp) {
        if (cacheLoginClientInfo == null && bindCtx == null) {
            return;
        }
        LoginClientInfo oldClientInfo = cacheLoginClientInfo;
        if (oldClientInfo == null) {
            oldClientInfo = ChannelAttrUtil.getChannelAttribute(bindCtx.channel(), MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        }
        if (oldClientInfo == null) {
            if (bindCtx != null && bindCtx.channel().isActive()) {
                bindCtx.close();
            }
            return;
        }
        if (!ClientHelper.isDirectoryOnline(oldClientInfo) && bindCtx == null) {
            return;
        }
        notifyPreviousSession(packet, mqttLogin, oldClientInfo, loginTimestamp);
        if (bindCtx != null && bindCtx.channel().isActive()) {
            bindCtx.close();
        }
    }

    private void notifyPreviousSession(Packet packet, MqttLoginClientInfo mqttLogin,
                                       LoginClientInfo oldClientInfo, long loginTimestamp) {
        Target kickTarget = MessageHelper.buildTarget(oldClientInfo);
        if (oldClientInfo instanceof MqttLoginClientInfo mqttOld) {
            MqttVersion oldVersion = MqttCodecUtil.getMqttVersion(mqttOld.getVersion());
            if (oldVersion == null) {
                return;
            }
            MqttMessage disconnect = MqttMessageFactory.newMessage(
                    new MqttFixedHeader(MqttMessageType.DISCONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0),
                    null, null);
            Metadata metadata = new Metadata();
            metadata.setAppKey(mqttOld.getAppKey());
            Message kickMessage = new Message(
                    MessageContext.idGenerator().generateIdStr(),
                    null,
                    mqttOld.getIdentity(),
                    MqttMessageContentTypeEnum.MQTT_DISCONNECT.getType(),
                    MqttCodecUtil.encode(oldVersion, disconnect),
                    loginTimestamp,
                    metadata);
            Packet kickPacket = new Packet(
                    mqttOld.getProtocol(), mqttOld.getProtocolVersion(),
                    MessageContext.idGenerator().generateId(), mqttOld.getDeviceType(),
                    NetworkEnum.OTHER.getValue(), Encrypt.SymmetryEncrypt.NONE.getValue(),
                    Serializer.PROTO_STUFF.getValue(), MqttMessageTypeEnum.MQTT.getType(),
                    oldVersion.protocolLevel(), kickMessage);
            MessageHelper.syncSendMessageWithoutInterceptor(kickPacket, kickTarget);
            return;
        }
        Message kickMessage = new Message(
                MessageContext.idGenerator().generateIdStr(),
                null,
                mqttLogin.getIdentity(),
                MessageContentTypeEnum.REMOTE_LOGIN_CONTENT.getType(),
                Serializer.JSON.serializeToString(new ServerNotifyContent(
                        String.format(MessageConstant.REMOTE_LOGIN_NOTIFICATIONS, clientIpOf(packet)))),
                loginTimestamp,
                packet.getMessage().getMetadata());
        Packet kickPacket = new Packet(
                oldClientInfo.getProtocol(), oldClientInfo.getProtocolVersion(),
                MessageContext.idGenerator().generateId(), oldClientInfo.getDeviceType(),
                NetworkEnum.OTHER.getValue(), packet.getEncryptType(), packet.getSerializeAlgorithm(),
                MessageTypeEnum.SERVER_NOTIFY.getType(), kickMessage);
        MessageHelper.syncSendMessageWithoutInterceptor(kickPacket, kickTarget);
    }

    private static String clientIpOf(Packet packet) {
        if (packet.getMessage() == null || packet.getMessage().getMetadata() == null) {
            return "";
        }
        return StringUtils.defaultString(packet.getMessage().getMetadata().getClientIp());
    }

    private void installCloseHook(ChannelHandlerContext ctx, String comboIdentity, MqttLoginClientInfo loginClientInfo) {
        Consumer<Channel> channelConsumer = channel -> {
            log.warn("客户端断开连接, 触发回调, comboIdentity: {}, channel: {}", comboIdentity, channel);
            LoginClientInfo attrLogin = ChannelAttrUtil.getChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
            LoginClientInfo closingLocalLoginClientInfo = attrLogin != null ? attrLogin : loginClientInfo;
            Byte clientLoginDeviceValue = closingLocalLoginClientInfo.getDeviceType();
            String closingComboIdentity = IdentityUtil.generalComboIdentity(
                    closingLocalLoginClientInfo.getAppKey(), closingLocalLoginClientInfo.getIdentity(), clientLoginDeviceValue);
            ClientHelper.unregisterLocal(closingComboIdentity, ctx, closingLocalLoginClientInfo.getAppKey());
            if (attrLogin == null) {
                return;
            }
            ThreadPoolManager.messageProcessorExecutor().execute(() ->
                    unbindMqttRemoteOnClose(closingLocalLoginClientInfo, closingComboIdentity));
        };
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_CHANNEL_CLOSE_HOOK, channelConsumer);
    }

    private void unbindMqttRemoteOnClose(LoginClientInfo closingLocalLoginClientInfo, String closingComboIdentity) {
        String loginClientInfoCacheKey = CacheConstant.buildLoginCacheKey(
                closingLocalLoginClientInfo.getAppKey(), closingComboIdentity);
        boolean locked = ClientHelper.tryRunWithBindLock(
                closingLocalLoginClientInfo.getAppKey(), closingComboIdentity, () ->
                        unbindMqttIfSameSession(closingLocalLoginClientInfo, loginClientInfoCacheKey, closingComboIdentity));
        if (!locked) {
            log.error("mqtt客户端: {} 解绑登录信息失败,原因：获取分布式锁超时", closingLocalLoginClientInfo);
        }
        MessageServerContext.publishEvent(new MessageEvent(closingLocalLoginClientInfo, MessageEventTypeEnum.CLIENT_LOGOUT), true);
    }

    private void unbindMqttIfSameSession(LoginClientInfo closingLocalLoginClientInfo,
                                         String loginClientInfoCacheKey, String closingComboIdentity) {
        LoginClientInfo closingRemoteLoginClientInfo = MessageServerContext.remoteLoginClientInfoCache.get(loginClientInfoCacheKey);
        if (!(closingRemoteLoginClientInfo instanceof MqttLoginClientInfo closingRemoteMqttLoginClientInfo)) {
            return;
        }
        if (!closingLocalLoginClientInfo.getLoginServerAddress().equals(closingRemoteMqttLoginClientInfo.getLoginServerAddress())
                || closingRemoteMqttLoginClientInfo.getLastLoginTime() != closingLocalLoginClientInfo.getLastLoginTime()) {
            log.warn("mqtt客户端: {} 解绑登录信息跳过,原因：登录地址或时间戳不匹配（新连接已覆盖）", closingLocalLoginClientInfo);
            return;
        }
        if (closingRemoteMqttLoginClientInfo.getCleanSession() == NumberConstant.NUMBER_1) {
            LoginSessionDirectory.unbind(closingLocalLoginClientInfo, closingComboIdentity);
            return;
        }
        if (closingRemoteMqttLoginClientInfo.getCleanSession() == NumberConstant.NUMBER_0) {
            LoginSessionDirectory.unbindRouteKeepLogin(closingLocalLoginClientInfo);
            closingRemoteMqttLoginClientInfo.setOnlineStatus(OnlineEnum.OFFLINE);
            MessageServerContext.remoteLoginClientInfoCache.put(
                    loginClientInfoCacheKey,
                    closingRemoteMqttLoginClientInfo.copyForRedis(),
                    closingRemoteMqttLoginClientInfo.getSessionExpiryInterval(),
                    TimeUnit.SECONDS);
        }
    }

    private void refuseDecoderFailure(ChannelHandlerContext ctx, Packet packet, Throwable cause) {
        if (cause instanceof MqttUnacceptableProtocolVersionException) {
            refuse(ctx, packet, MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION);
            return;
        }
        if (cause instanceof MqttIdentifierRejectedException) {
            refuse(ctx, packet, MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED);
            return;
        }
        ctx.close();
    }

    private void refuse(ChannelHandlerContext ctx, Packet packet, MqttConnectReturnCode returnCode) {
        MqttMessage connAckMessage = MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                new MqttConnAckVariableHeader(returnCode, false), null);
        if (ctx.channel().eventLoop().inEventLoop()) {
            MessageHelper.tryWriteObject(ctx.channel(), connAckMessage, packet, sendResult -> {});
        } else {
            ctx.channel().eventLoop().execute(() ->
                    MessageHelper.tryWriteObject(ctx.channel(), connAckMessage, packet, sendResult -> {}));
        }
        ctx.close();
    }

    /**
     * Redis 与本地注册表绑定成功后发送 CONNACK 并安装心跳管道。
     */
    private void completeMqttConnectAfterRemoteBind(ChannelHandlerContext ctx, Packet packet,
                                                    MqttConnectMessage mqttConnectMessage, boolean sessionPresent,
                                                    MqttLoginClientInfo loginClientInfo, Throwable bindError) {
        if (!ctx.channel().isActive()) {
            ClientHelper.unbindLocalRegisterTable(loginClientInfo, ctx);
            return;
        }
        if (bindError != null) {
            log.error("mqtt 客户端: {} 登录绑定失败", loginClientInfo.getIdentity(), bindError);
            MqttMessage connAckMessage = MqttMessageFactory.newMessage(
                    new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                    new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE, false),
                    null);
            MessageHelper.tryWriteObject(ctx.channel(), connAckMessage, packet, sendResult -> {});
            ctx.close();
            return;
        }
        int heartbeatExpireTime = loginClientInfo.getHeartBeatTimeout();
        if (MessageServerContext.serverProperties().isClientHeartBeatEnable() && heartbeatExpireTime > 0) {
            ctx.pipeline()
                    .addAfter(MessageConstant.CONVERT_2_PACKET_HANDLER, MessageConstant.HEART_BEAT_IDLE_HANDLER,
                            new IdleStateHandler(heartbeatExpireTime, NumberConstant.NUMBER_0, NumberConstant.NUMBER_0))
                    .addAfter(MessageConstant.HEART_BEAT_IDLE_HANDLER, MessageConstant.HEART_BEAT_HANDLER, new HeartBeatHandler());
        }
        MqttMessage mqttConnAckMessage = MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_ACCEPTED, sessionPresent), null);
        ctx.writeAndFlush(mqttConnAckMessage);
        log.debug("CONNECT - clientId: {}, cleanSession: {}", mqttConnectMessage.payload().clientIdentifier(),
                mqttConnectMessage.variableHeader().isCleanSession());
        String comboIdentity = IdentityUtil.generalComboIdentity(
                loginClientInfo.getAppKey(), loginClientInfo.getIdentity(), DeviceTypeEnum.M.getType());
        if (mqttConnectMessage.variableHeader().isCleanSession()) {
            MqttRepository.INSTANCE.clearInflight(loginClientInfo.getAppKey(), comboIdentity);
        } else {
            replayMqttInflight(ctx, packet, loginClientInfo.getAppKey(), comboIdentity);
        }
    }

    /**
     * cleanSession=0 时重发未收到 PUBACK 的 QoS1 报文（DUP=1）。
     */
    private void replayMqttInflight(ChannelHandlerContext ctx, Packet packet, String appKey, String comboIdentity) {
        Map<Integer, String> inflight = MqttRepository.INSTANCE.loadInflight(appKey, comboIdentity);
        if (inflight.isEmpty()) {
            return;
        }
        MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(packet.getRetain());
        if (mqttVersion == null) {
            mqttVersion = MqttVersion.MQTT_3_1_1;
        }
        for (Map.Entry<Integer, String> entry : inflight.entrySet()) {
            MqttMessage stored = MqttCodecUtil.decode(mqttVersion, entry.getValue());
            if (!(stored instanceof MqttPublishMessage publish)) {
                continue;
            }
            MqttPublishMessage dup = (MqttPublishMessage) MqttMessageFactory.newMessage(
                    new MqttFixedHeader(MqttMessageType.PUBLISH, true, publish.fixedHeader().qosLevel(),
                            false, 0),
                    publish.variableHeader(),
                    publish.payload().retainedDuplicate());
            MessageHelper.tryWriteObject(ctx.channel(), dup, packet, sendResult -> {});
        }
    }

    /**
     * 校验登录信息；MQTT 走 {@link #authenticate}。
     */
    public boolean validate(LoginContent loginContent) {
        return LoginAuthValidator.verify(loginContent);
    }
}
