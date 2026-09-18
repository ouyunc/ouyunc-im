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
import com.ouyunc.core.device.DeviceTypeRegistry;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ClientLoginEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.handler.HeartBeatHandler;
import com.ouyunc.message.handler.LoginTimeoutSupport;
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
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * mqtt connect。username=appKey，password={@code createTime#signature}，与原生登录同一套 MD5 签名。
 */
public class MqttConnectMessageContentBiProcessor extends AbstractBaseBiProcessor<Mono<Void>, Integer> {
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
    public Mono<Void> process(ChannelHandlerContext ctx, Packet packet) {
        // 有序队列需等到 bind/CONNACK 完成后再放行同 channel 下一包
        return Mono.create(sink -> {
            try {
                doConnect(ctx, packet, sink);
            } catch (Throwable t) {
                sink.error(t);
            }
        });
    }

    /**
     * CONNECT 鉴权与异步绑定；CAS 完成后由 {@link #completeMqttConnectAfterRemoteBind} 回写 CONNACK。
     */
    private void doConnect(ChannelHandlerContext ctx, Packet packet, MonoSink<Void> sink) {
            long loginTimestamp = TimeUtil.currentTimeMillis();
            Message connectMessage = packet.getMessage();
            MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(packet.getRetain());
            if (mqttVersion == null) {
                log.error("MqttConnectMessageProcessor connect 消息解码失败，请检查协议版本是否正确！");
                sink.success();
                return;
            }
            if (!MessageServerContext.isAcceptingNewConnections()) {
                log.warn("MQTT CONNECT 被拒绝：服务摘流中 channel={}", ctx.channel().id().asShortText());
                refuse(ctx, packet, MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
                sink.success();
                return;
            }
            if (!MessageServerContext.serverProperties().isMqttEnabled()) {
                log.warn("MQTT CONNECT 被拒绝：mqtt.enabled=false, channel={}", ctx.channel().id().asShortText());
                refuse(ctx, packet, MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
                sink.success();
                return;
            }
            MqttMessage mqttMessage = MqttCodecUtil.decode(mqttVersion, connectMessage.getContent());
            if (!(mqttMessage instanceof MqttConnectMessage mqttConnectMessage)) {
                log.error("mqtt 非法连接 connect 消息！");
                sink.success();
                return;
            }
            if (mqttMessage.decoderResult().isFailure()) {
                refuseDecoderFailure(ctx, packet, mqttMessage.decoderResult().cause());
                sink.success();
                return;
            }
            MqttConnectPayload mqttConnectPayload = mqttConnectMessage.payload();
            if (StringUtils.isBlank(mqttConnectPayload.clientIdentifier())) {
                refuse(ctx, packet, MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED);
                sink.success();
                return;
            }
            MqttConnectVariableHeader mqttConnectVariableHeader = mqttConnectMessage.variableHeader();
            String appKey = mqttConnectPayload.userName();
            byte[] passwordBytes = mqttConnectPayload.passwordInBytes();
            String password = passwordBytes == null ? null : new String(passwordBytes, CharsetUtil.UTF_8);
            LoginAuthValidator.MqttPassword mqttPassword = LoginAuthValidator.parseMqttPassword(password);
            if (mqttPassword == null) {
                refuse(ctx, packet, MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
                sink.success();
                return;
            }
            MqttLoginClientInfo mqttLoginClientInfo = buildMqttLogin(ctx, mqttConnectPayload, mqttConnectVariableHeader,
                    mqttVersion, appKey, mqttPassword, loginTimestamp);
            if (mqttLoginClientInfo == null || !authenticate(ctx, mqttLoginClientInfo)) {
                refuse(ctx, packet, MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
                sink.success();
                return;
            }
            if (ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN) != null
                    || Boolean.TRUE.equals(ChannelAttrUtil.getChannelAttribute(ctx,
                    MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT))) {
                log.warn("MQTT 重复 CONNECT 忽略 channelId={}", ctx.channel().id().asShortText());
                sink.success();
                return;
            }
            ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT, Boolean.TRUE);
            String comboIdentity = IdentityUtil.generalComboIdentity(
                    mqttLoginClientInfo.getAppKey(), mqttLoginClientInfo.getIdentity(), DeviceTypeEnum.M.getType());
            // sessionPresent 以绑定前目录是否存在为准，但踢旧必须在 CAS 绑定胜出之后
            LoginClientInfo cacheLoginClientInfo = MessageServerContext.remoteLoginClientInfoCache.get(
                    CacheConstant.buildLoginCacheKey(mqttLoginClientInfo.getAppKey(), comboIdentity));
            boolean sessionPresent = cacheLoginClientInfo != null && !mqttConnectMessage.variableHeader().isCleanSession();
            installCloseHook(ctx, comboIdentity, mqttLoginClientInfo);
            ClientHelper.bindAsync(ctx, mqttLoginClientInfo).whenComplete((previous, ex) -> {
                try {
                    ThreadPoolManager.messageProcessorExecutor().execute(() ->
                            finishMqttConnectAfterDirectoryCheck(ctx, packet, mqttConnectMessage, sessionPresent,
                                    mqttLoginClientInfo, previous, loginTimestamp, ex, sink));
                } catch (RejectedExecutionException scheduleError) {
                    log.error("MQTT fencing 投递业务线程被拒绝 channelId={}", ctx.channel().id().asShortText(), scheduleError);
                    ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT, null);
                    AppKeyValidator.releaseReservedIfNeeded(mqttLoginClientInfo.getAppKey(), ctx);
                    if (ctx.channel().isActive()) {
                        ctx.channel().close();
                    }
                    sink.success();
                }
            });
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
     * appKey 配额、设备白名单、MQTT 所需 {@link DeviceTypeEnum#M}、签名与客服建档。
     */
    private boolean authenticate(ChannelHandlerContext ctx, MqttLoginClientInfo loginClientInfo) {
        loginClientInfo.setScope(LoginScopeEnum.NORMAL.getType());
        if (!AppKeyValidator.INSTANCE.tryReserveForLogin(loginClientInfo.getAppKey(), ctx)) {
            return false;
        }
        if (!DeviceTypeRegistry.supports(
                loginClientInfo.getAppKey(), loginClientInfo.getIdentity(), DeviceTypeEnum.M.getType())) {
            log.warn("MQTT 登录拒绝：appKey={} 未开通设备类型 M", loginClientInfo.getAppKey());
            AppKeyValidator.releaseReservedIfNeeded(loginClientInfo.getAppKey(), ctx);
            return false;
        }
        if (!LoginAuthValidator.verify(loginClientInfo)) {
            AppKeyValidator.releaseReservedIfNeeded(loginClientInfo.getAppKey(), ctx);
            return false;
        }
        return true;
    }

    /**
     * CAS 绑定胜出后顶号：本机旧连接已在 bind 时关闭；跨节点发 DISCONNECT / 远程登录通知。
     */
    private void kickPreviousMqttSessionAfterBindWin(Packet packet, MqttLoginClientInfo mqttLogin,
                                                     LoginClientInfo previous, long loginTimestamp) {
        if (previous == null) {
            return;
        }
        String local = MessageContext.messageProperties.getLocalServerAddress();
        if (StringUtils.isNotBlank(previous.getLoginServerAddress())
                && previous.getLoginServerAddress().equals(local)) {
            return;
        }
        notifyPreviousSession(packet, mqttLogin, previous, loginTimestamp);
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
            ClientHelper.unregisterLocal(closingComboIdentity, channel, closingLocalLoginClientInfo.getAppKey());
            AppKeyValidator.releaseReservedIfNeeded(closingLocalLoginClientInfo.getAppKey(), channel);
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
            log.error("mqtt客户端 {} 解绑登录信息失败,原因：获取分布式锁超时", closingLocalLoginClientInfo);
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
            log.warn("mqtt客户端 {} 解绑登录信息跳过,原因：登录地址或时间戳不匹配（新连接已覆盖）", closingLocalLoginClientInfo);
            return;
        }
        if (closingRemoteMqttLoginClientInfo.getCleanSession() == NumberConstant.NUMBER_1) {
            LoginSessionDirectory.unbind(closingLocalLoginClientInfo, closingComboIdentity);
            return;
        }
        if (closingRemoteMqttLoginClientInfo.getCleanSession() == NumberConstant.NUMBER_0) {
            LoginSessionDirectory.unbindRouteKeepLogin(closingLocalLoginClientInfo);
            LoginSessionDirectory.persistOrExpireLogin(
                    closingLocalLoginClientInfo.getAppKey(),
                    closingComboIdentity,
                    closingRemoteMqttLoginClientInfo.getSessionExpiryInterval());
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
     * Redis fencing、踢人、inflight 清理/加载在业务线程完成，再回 EventLoop 写 CONNACK。
     */
    private void finishMqttConnectAfterDirectoryCheck(ChannelHandlerContext ctx, Packet packet,
                                                      MqttConnectMessage mqttConnectMessage, boolean sessionPresent,
                                                      MqttLoginClientInfo loginClientInfo, LoginClientInfo previous,
                                                      long loginTimestamp, Throwable bindError, MonoSink<Void> sink) {
        boolean directoryOwned = false;
        Map<Integer, String> inflight = Collections.emptyMap();
        if (bindError == null && ctx.channel().isActive()) {
            directoryOwned = ClientHelper.stillOwnsDirectory(loginClientInfo);
            if (directoryOwned) {
                kickPreviousMqttSessionAfterBindWin(packet, loginClientInfo, previous, loginTimestamp);
                directoryOwned = ClientHelper.stillOwnsDirectory(loginClientInfo);
            }
            if (directoryOwned && ctx.channel().isActive()) {
                String comboIdentity = IdentityUtil.generalComboIdentity(
                        loginClientInfo.getAppKey(), loginClientInfo.getIdentity(), DeviceTypeEnum.M.getType());
                if (mqttConnectMessage.variableHeader().isCleanSession()) {
                    MqttRepository.INSTANCE.clearInflight(loginClientInfo.getAppKey(), comboIdentity);
                } else {
                    inflight = MqttRepository.INSTANCE.loadInflight(loginClientInfo.getAppKey(), comboIdentity);
                }
            }
        }
        final boolean owned = directoryOwned;
        final Map<Integer, String> inflightSnapshot = inflight == null ? Collections.emptyMap() : inflight;
        try {
            ctx.executor().execute(() -> {
                try {
                    completeMqttConnectAfterRemoteBind(ctx, packet, mqttConnectMessage, sessionPresent,
                            loginClientInfo, loginTimestamp, bindError, owned, inflightSnapshot);
                    sink.success();
                } catch (Throwable t) {
                    ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT, null);
                    sink.error(t);
                }
            });
        } catch (Throwable scheduleError) {
            log.error("MQTT CONNACK 投递 EventLoop 被拒绝 channelId={}", ctx.channel().id().asShortText(), scheduleError);
            ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT, null);
            AppKeyValidator.releaseReservedIfNeeded(loginClientInfo.getAppKey(), ctx);
            if (ctx.channel().isActive()) {
                ctx.channel().close();
            }
            sink.success();
        }
    }

    /**
     * 在 EventLoop 上发送 CONNACK 并安装心跳管道。fencing / inflight Redis 已在业务线程做完。
     */
    private void completeMqttConnectAfterRemoteBind(ChannelHandlerContext ctx, Packet packet,
                                                    MqttConnectMessage mqttConnectMessage, boolean sessionPresent,
                                                    MqttLoginClientInfo loginClientInfo, long loginTimestamp,
                                                    Throwable bindError, boolean directoryOwned,
                                                    Map<Integer, String> inflight) {
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT, null);
        if (!ctx.channel().isActive()) {
            ClientHelper.unbindLocalRegisterTable(loginClientInfo, ctx);
            AppKeyValidator.releaseReservedIfNeeded(loginClientInfo.getAppKey(), ctx);
            return;
        }
        if (bindError != null) {
            log.error("mqtt 客户端 {} 登录绑定失败", loginClientInfo.getIdentity(), bindError);
            MqttMessage connAckMessage = MqttMessageFactory.newMessage(
                    new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                    new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE, false),
                    null);
            MessageHelper.tryWriteObject(ctx.channel(), connAckMessage, packet, sendResult -> {});
            ClientHelper.unbindLocalRegisterTable(loginClientInfo, ctx);
            AppKeyValidator.releaseReservedIfNeeded(loginClientInfo.getAppKey(), ctx);
            ctx.close();
            return;
        }
        if (!directoryOwned) {
            log.warn("mqtt 登录 fencing 失败，目录已被更新会话覆盖 clientId={}", loginClientInfo.getIdentity());
            MqttMessage connAckMessage = MqttMessageFactory.newMessage(
                    new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                    new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE, false),
                    null);
            MessageHelper.tryWriteObject(ctx.channel(), connAckMessage, packet, sendResult -> {});
            ClientHelper.unbindLocalRegisterTable(loginClientInfo, ctx);
            AppKeyValidator.releaseReservedIfNeeded(loginClientInfo.getAppKey(), ctx);
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
        LoginTimeoutSupport.cancel(ctx);
        MessageServerContext.publishEvent(
                new MessageEvent(new ClientLoginEventPayload(loginClientInfo, ctx),
                        MessageEventTypeEnum.CLIENT_LOGIN, loginTimestamp),
                true);
        log.debug("CONNECT - clientId: {}, cleanSession: {}", mqttConnectMessage.payload().clientIdentifier(),
                mqttConnectMessage.variableHeader().isCleanSession());
        if (!mqttConnectMessage.variableHeader().isCleanSession()) {
            replayMqttInflight(ctx, packet, inflight);
        }
    }

    /**
     * cleanSession=0 时重发未收到 PUBACK 的 QoS1 报文（DUP=1）。inflight 已在业务线程从 Redis 取出。
     */
    private void replayMqttInflight(ChannelHandlerContext ctx, Packet packet, Map<Integer, String> inflight) {
        if (inflight == null || inflight.isEmpty()) {
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
