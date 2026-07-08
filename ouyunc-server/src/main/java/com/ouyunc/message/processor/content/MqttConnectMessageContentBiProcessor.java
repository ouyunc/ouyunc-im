package com.ouyunc.message.processor.content;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.DeviceTypeEnum;
import com.ouyunc.base.constant.enums.MessageContentType;
import com.ouyunc.base.constant.enums.MqttMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.LoginScopeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.OnlineEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.MqttLoginClientInfo;
import com.ouyunc.base.model.Protocol;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.MqttCodecUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.base.constant.enums.YesOrNo;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.handler.HeartBeatHandler;
import com.ouyunc.message.handler.LoginKeepAliveHandler;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.processor.AbstractBaseBiProcessor;
import com.ouyunc.message.protocol.NativePacketProtocol;
import com.ouyunc.repository.MqttRepository;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * mqtt connect
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
        MqttMessage mqttMessage = MqttCodecUtil.decode(mqttVersion, connectMessage.getContent());
        if (mqttMessage instanceof MqttConnectMessage mqttConnectMessage) {
            // 消息解码器出现异常
            if (mqttMessage.decoderResult().isFailure()) {
                Throwable cause = mqttMessage.decoderResult().cause();
                if (cause instanceof MqttUnacceptableProtocolVersionException) {
                    // 不支持的协议版本
                    MqttMessage connAckMessage = MqttMessageFactory.newMessage(
                            new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                            new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION, false), null);
                    if (ctx.channel().eventLoop().inEventLoop()) {
                        if (!MessageHelper.tryWriteObject(ctx.channel(), connAckMessage, packet, (sendResult)->{})) {
                            return;
                        }
                    } else {
                        ctx.channel().eventLoop().execute(() -> MessageHelper.tryWriteObject(ctx.channel(), connAckMessage, packet,  (sendResult)->{}));
                    }
                    ctx.close();
                    return;
                } else if (cause instanceof MqttIdentifierRejectedException) {
                    // 不合格的clientId
                    MqttMessage connAckMessage = MqttMessageFactory.newMessage(
                            new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                            new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, false), null);
                    if (ctx.channel().eventLoop().inEventLoop()) {
                        if (!MessageHelper.tryWriteObject(ctx.channel(), connAckMessage, packet, (sendResult)->{})) {
                            return;
                        }
                    } else {
                        ctx.channel().eventLoop().execute(() -> MessageHelper.tryWriteObject(ctx.channel(), connAckMessage, packet, (sendResult)->{}));
                    }
                    ctx.close();
                    return;
                }
                ctx.close();
                return;
            }
            // clientId为空或null的情况, 这里要求客户端必须提供clientId, 不管cleanSession是否为1, 此处没有参考标准协议实现
            if (StringUtils.isBlank(mqttConnectMessage.payload().clientIdentifier())) {
                MqttMessage connAckMessage = MqttMessageFactory.newMessage(
                        new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                        new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, false), null);
                if (ctx.channel().eventLoop().inEventLoop()) {
                    if (!MessageHelper.tryWriteObject(ctx.channel(), connAckMessage, packet , (sendResult)->{})) {
                        return;
                    }
                } else {
                    ctx.channel().eventLoop().execute(() -> MessageHelper.tryWriteObject(ctx.channel(), connAckMessage, packet, (sendResult)->{}));
                }
                ctx.close();
                return;
            }
            // 用户名和密码验证, 这里要求客户端连接时必须提供用户名和密码, 不管是否设置用户名标志和密码标志为1, 此处没有参考标准协议实现
            // 这里username 就是appKey; password 就是 signature
            MqttConnectVariableHeader mqttConnectVariableHeader = mqttConnectMessage.variableHeader();
            MqttConnectPayload mqttConnectPayload = mqttConnectMessage.payload();
            String appKey = mqttConnectPayload.userName();
            byte[] passwordBytes = mqttConnectPayload.passwordInBytes();
            String signature =  passwordBytes == null ? null : new String(passwordBytes, CharsetUtil.UTF_8);
            byte[] willMessageInBytes = mqttConnectPayload.willMessageInBytes();
            String willMessage = willMessageInBytes == null ? null : new String(willMessageInBytes, CharsetUtil.UTF_8);
            String willTopic = mqttConnectPayload.willTopic();
            int enableWill = mqttConnectVariableHeader.isWillFlag() ? NumberConstant.NUMBER_1 : NumberConstant.NUMBER_0;
            int qos = mqttConnectVariableHeader.willQos();
            int cleanSession = mqttConnectVariableHeader.isCleanSession() ? NumberConstant.NUMBER_1 : NumberConstant.NUMBER_0;
            int isWillRetain = mqttConnectVariableHeader.isWillRetain() ? NumberConstant.NUMBER_1 : NumberConstant.NUMBER_0;
            byte version = mqttVersion.protocolLevel();
            // 心跳
            int keepAlive = mqttConnectVariableHeader.keepAliveTimeSeconds();
            // 永不过期
            int sessionExpiryInterval = NumberConstant.NUMBER_NEGATIVE_1;
            // 构造登录消息
            MqttProperties.MqttProperty<Integer> sessionExpiryIntervalProperty = mqttConnectVariableHeader.properties().getProperty(MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL.value());
            if (sessionExpiryIntervalProperty != null) {
                sessionExpiryInterval = sessionExpiryIntervalProperty.value();
            }
            Protocol protocol = ctx.channel().attr(NativePacketProtocol.protocolAttrKey).get();
            if (protocol == null) {
                log.warn("Protocol not set on channel, closing MQTT connection");
                ctx.close();
                return;
            }
            byte protocolValue = protocol.getProtocol();
            byte protocolVersion = protocol.getProtocolVersion();
            MqttLoginClientInfo mqttLoginClientInfo = new MqttLoginClientInfo(protocolValue, protocolVersion, MessageContext.messageProperties.getLocalServerAddress(), OnlineEnum.ONLINE, null, ClientHelper.calculateClientLoginExpireTime(keepAlive), ClientHelper.calculateClientHeartBeatTimeout(keepAlive), loginTimestamp, appKey, mqttConnectPayload.clientIdentifier(), DeviceTypeEnum.M.getType(), null, mqttConnectPayload.clientIdentifier(), signature, Encrypt.AsymmetricEncrypt.MD5.getValue(), keepAlive, loginTimestamp, enableWill, qos, version, isWillRetain, willMessage, willTopic, cleanSession, sessionExpiryInterval, YesOrNo.NO.getCode(), null);
            if (!validate(mqttLoginClientInfo)) {
                MqttMessage connAckMessage = MqttMessageFactory.newMessage(
                        new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                        new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED, false), null);
                if (ctx.channel().eventLoop().inEventLoop()) {
                    if (!MessageHelper.tryWriteObject(ctx.channel(), connAckMessage, packet, (sendResult)->{})) {
                        return;
                    }
                } else {
                    ctx.channel().eventLoop().execute(() -> MessageHelper.tryWriteObject(ctx.channel(), connAckMessage, packet, sendResult -> {}));
                }
                ctx.close();
                return;
            }
            String comboIdentity = IdentityUtil.generalComboIdentity(mqttLoginClientInfo.getAppKey(), mqttLoginClientInfo.getIdentity(), DeviceTypeEnum.M.getType());
            //如果之前已经登录（重复登录请求），这里判断是否已经登录过,同一个账号在同一个设备不能同时登录
            //1,从分布式缓存取出该登录用户
            LoginClientInfo cacheLoginClientInfo = MessageServerContext.remoteLoginClientInfoCache.get(CacheConstant.buildLoginCacheKey(mqttLoginClientInfo.getAppKey(), comboIdentity));
            //2,从本地用户注册表中取出该用户的channel
            ChannelHandlerContext bindCtx = MessageServerContext.localLoginClientRegisterTable.get(comboIdentity);
            // 如果还在当前服务登录的话，先关闭之前的连接(这里没有强制去通知让原来的连接进行跨服务下线，只是通过心跳让其自动感知下线)， 如果不在该服务器再次登录，也是需要关闭之前的channel,否则，如果当前登录绑定了信息，后面另外的channel 在关闭然后触发关闭事件，导致删除失败就会把缓存的登录信息给删掉
            if (bindCtx != null) {
                // @todo 关闭dup qos1
                if (cleanSession == NumberConstant.NUMBER_1) {
                    //dupPublishMessageStoreService.removeByClient(msg.payload().clientIdentifier());
			    }
                // 如果之前有绑定信息，且不为空，这里会触发close 监听事件，进而会删除本地缓存和远端缓存，注意这里是异步执行
                bindCtx.close();
            }
            // sessionPresent
            boolean sessionPresent = cacheLoginClientInfo != null && !mqttConnectMessage.variableHeader().isCleanSession();
            final MqttLoginClientInfo loginClientInfo = mqttLoginClientInfo;
            // 处理回调信息（channel close 在 EventLoop 上触发，分布式锁与 Redis I/O 必须移到业务线程池执行）
            Consumer<Channel> channelConsumer = channel -> {
                log.warn("客户端断开连接, 触发回调, comboIdentity: {}, channel: {}", comboIdentity, channel);
                final LoginClientInfo closingLocalLoginClientInfo = ChannelAttrUtil.getChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
                if (closingLocalLoginClientInfo == null) {
                    return;
                }
                Byte clientLoginDeviceValue = closingLocalLoginClientInfo.getDeviceType();
                String closingComboIdentity = IdentityUtil.generalComboIdentity(closingLocalLoginClientInfo.getAppKey(), closingLocalLoginClientInfo.getIdentity(), clientLoginDeviceValue);
                // 本地注册表立即移除（无需异步、无锁）
                MessageServerContext.localLoginClientRegisterTable.delete(closingComboIdentity);

                // 分布式状态清理与事件发布异步执行，避免阻塞 EventLoop
                ThreadPoolManager.messageProcessorExecutor().execute(() -> {
                    String loginClientInfoCacheKey = CacheConstant.buildLoginCacheKey(closingLocalLoginClientInfo.getAppKey(), closingComboIdentity);
                    RLock lock = MessageServerContext.redissonClient.getLock(CacheConstant.buildIdentityBindOrUnbindLockCacheKey(closingLocalLoginClientInfo.getAppKey(), closingComboIdentity));
                    try {
                        if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                            try {
                                LoginClientInfo closingRemoteLoginClientInfo = MessageServerContext.remoteLoginClientInfoCache.get(loginClientInfoCacheKey);
                                if (closingRemoteLoginClientInfo instanceof MqttLoginClientInfo closingRemoteMqttLoginClientInfo) {
                                    // 比较登录服务器地址 + 时间戳，避免新连接的状态被旧 close 回调覆盖删除
                                    if (closingLocalLoginClientInfo.getLoginServerAddress().equals(closingRemoteMqttLoginClientInfo.getLoginServerAddress())
                                            && closingRemoteMqttLoginClientInfo.getLastLoginTime() == closingLocalLoginClientInfo.getLastLoginTime()) {
                                        // 按 MQTT 3.1.1 规范处理 cleanSession：
                                        //   cleanSession = 1：临时会话，断开时清除所有会话状态
                                        //   cleanSession = 0：持久会话，断开时仅标记离线，保留会话用于下次重连
                                        if (closingRemoteMqttLoginClientInfo.getCleanSession() == NumberConstant.NUMBER_1) {
                                            MessageServerContext.remoteLoginClientInfoCache.delete(loginClientInfoCacheKey);
                                        } else if (closingRemoteMqttLoginClientInfo.getCleanSession() == NumberConstant.NUMBER_0) {
                                            closingRemoteMqttLoginClientInfo.setOnlineStatus(OnlineEnum.OFFLINE);
                                            MessageServerContext.remoteLoginClientInfoCache.put(loginClientInfoCacheKey, closingRemoteMqttLoginClientInfo, closingRemoteMqttLoginClientInfo.getSessionExpiryInterval(), TimeUnit.SECONDS);
                                        }
                                    } else {
                                        log.warn("mqtt客户端: {} 解绑登录信息跳过,原因：登录地址或时间戳不匹配（新连接已覆盖）", closingLocalLoginClientInfo);
                                    }
                                }
                            } finally {
                                if (lock.isHeldByCurrentThread()) {
                                    lock.unlock();
                                }
                            }
                        } else {
                            log.error("mqtt客户端: {} 解绑登录信息失败,原因：获取分布式锁超时", closingLocalLoginClientInfo);
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("mqtt客户端: {} 解绑登录信息被中断", closingLocalLoginClientInfo);
                    } catch (Exception e) {
                        log.error("mqtt客户端: {} 解绑登录信息异常,原因：{}", closingLocalLoginClientInfo, e.getMessage(), e);
                    } finally {
                        // 发送客户端离线事件（无论分布式状态清理成功与否都要触发）
                        MessageServerContext.publishEvent(new MessageEvent(closingLocalLoginClientInfo, MessageEventTypeEnum.CLIENT_LOGOUT), true);
                    }
                });
            };
            // 设置channel关闭后的钩子
            ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_CHANNEL_CLOSE_HOOK, channelConsumer);
            ClientHelper.bindAsync(ctx, loginClientInfo).whenComplete((unused, ex) ->
                    ctx.executor().execute(() ->
                            completeMqttConnectAfterRemoteBind(ctx, packet, mqttConnectMessage, sessionPresent, loginClientInfo, ex)));
        }else {
            log.error("mqtt 非法连接connect 消息！");
        }
    }

    /**
     * Redis 与本地注册表绑定成功后发送 CONNACK 并安装心跳管道。
     */
    private void completeMqttConnectAfterRemoteBind(ChannelHandlerContext ctx, Packet packet,
                                                    MqttConnectMessage mqttConnectMessage, boolean sessionPresent,
                                                    MqttLoginClientInfo loginClientInfo, Throwable bindError) {
        if (!ctx.channel().isActive()) {
            ClientHelper.unbindLocalRegisterTable(loginClientInfo);
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
                    .addAfter(MessageConstant.CONVERT_2_PACKET_HANDLER, MessageConstant.CLIENT_LOGIN_KEEP_ALIVE_HANDLER, new LoginKeepAliveHandler())
                    .addAfter(MessageConstant.CLIENT_LOGIN_KEEP_ALIVE_HANDLER, MessageConstant.HEART_BEAT_IDLE_HANDLER,
                            new IdleStateHandler(heartbeatExpireTime, NumberConstant.NUMBER_0, NumberConstant.NUMBER_0))
                    .addAfter(MessageConstant.HEART_BEAT_IDLE_HANDLER, MessageConstant.HEART_BEAT_HANDLER, new HeartBeatHandler());
        }
        MqttMessage mqttConnAckMessage = MqttMessageFactory.newMessage(
                new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_ACCEPTED, sessionPresent), null);
        ctx.writeAndFlush(mqttConnAckMessage);
        log.debug("CONNECT - clientId: {}, cleanSession: {}", mqttConnectMessage.payload().clientIdentifier(),
                mqttConnectMessage.variableHeader().isCleanSession());
        // @todo 如果cleanSession为0, 需要重发同一clientId存储的未完成的QoS1和QoS2的DUP消息
    }

    /***
     * @author fzx
     * @description 校验登录信息；{@code scope} 必须为 {@link LoginScopeEnum} 已定义取值
     */
    public boolean validate(LoginContent loginContent) {
        return LoginScopeEnum.isDefinedType(loginContent.getScope());
    }
}
