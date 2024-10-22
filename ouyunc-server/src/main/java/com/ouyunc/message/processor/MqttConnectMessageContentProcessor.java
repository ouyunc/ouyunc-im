package com.ouyunc.message.processor;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.MqttCodecUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.listener.event.ClientLogoutEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.handler.HeartBeatHandler;
import com.ouyunc.message.handler.LoginKeepAliveHandler;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.repository.MqttRepository;
import io.netty.buffer.ByteBufAllocator;
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
public class MqttConnectMessageContentProcessor extends AbstractBaseProcessor<Integer>{
    private static final Logger log = LoggerFactory.getLogger(MqttConnectMessageContentProcessor.class);

    @Override
    public MessageContentType type() {
        return MqttMessageContentTypeEnum.MQTT_CONNECT;
    }

    @SuppressWarnings("unchecked")
    @Override
    public MqttRepository repository() {
        return new MqttRepository();
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.info("MqttConnectMessageProcessor connect 正在处理mqtt 连接消息...");
        long loginTimestamp = TimeUtil.currentTimeMillis();
        Message connectMessage = packet.getMessage();

        MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(packet.getRetain());
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
                    ctx.writeAndFlush(connAckMessage);
                    ctx.close();
                    return;
                } else if (cause instanceof MqttIdentifierRejectedException) {
                    // 不合格的clientId
                    MqttMessage connAckMessage = MqttMessageFactory.newMessage(
                            new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                            new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, false), null);
                    ctx.writeAndFlush(connAckMessage);
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
                ctx.writeAndFlush(connAckMessage);
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
            // 永不过期
            int sessionExpiryInterval = MessageConstant.MINUS_ONE;
            // 构造登录消息
            MqttProperties.MqttProperty<Integer> sessionExpiryIntervalProperty = mqttConnectVariableHeader.properties().getProperty(MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL.value());
            if (sessionExpiryIntervalProperty != null) {
                sessionExpiryInterval = sessionExpiryIntervalProperty.value();
            }
            // @todo 根据自己的业务这里进行修改
            LoginContent loginContent = new LoginContent(appKey, mqttConnectPayload.clientIdentifier(), DeviceTypeEnum.IOT, signature, Encrypt.AsymmetricEncrypt.MD5.getValue(), mqttConnectVariableHeader.keepAliveTimeSeconds(), mqttConnectVariableHeader.isWillFlag() ? MessageConstant.ONE : MessageConstant.ZERO, new String(mqttConnectPayload.willMessageInBytes(), CharsetUtil.UTF_8), mqttConnectPayload.willTopic(), mqttConnectVariableHeader.isCleanSession()? MessageConstant.ONE : MessageConstant.ZERO, sessionExpiryInterval, connectMessage.getCreateTime());
            if (!validate(loginContent)) {
                MqttMessage connAckMessage = MqttMessageFactory.newMessage(
                        new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                        new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED, false), null);
                ctx.writeAndFlush(connAckMessage);
                ctx.close();
                return;
            }
            DeviceType deviceType = DeviceTypeEnum.IOT;
            String comboIdentity = IdentityUtil.generalComboIdentity(loginContent.getAppKey(), loginContent.getIdentity(), deviceType.getDeviceTypeName());
            String clientLoginCacheKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + loginContent.getAppKey() + CacheConstant.COLON + CacheConstant.LOGIN + CacheConstant.USER + comboIdentity;
            //如果之前已经登录（重复登录请求），这里判断是否已经登录过,同一个账号在同一个设备不能同时登录
            //1,从分布式缓存取出该登录用户
            LoginClientInfo cacheLoginClientInfo = MessageServerContext.remoteLoginClientInfoCache.get(clientLoginCacheKey);
            //2,从本地用户注册表中取出该用户的channel
            ChannelHandlerContext bindCtx = MessageServerContext.localClientRegisterTable.get(comboIdentity);
            // 如果还在当前服务登录的话，先关闭之前的连接(这里没有强制去通知让原来的连接进行跨服务下线，只是通过心跳让其自动感知下线)， 如果不在该服务器再次登录，也是需要关闭之前的channel,否则，如果当前登录绑定了信息，后面另外的channel 在关闭然后触发关闭事件，导致删除失败就会把缓存的登录信息给删掉
            if (bindCtx != null) {
                // 如果之前有绑定信息，且不为空，这里会触发close 监听事件，进而会删除本地缓存和远端缓存，注意这里是异步执行，可能会影响绑定的信息
                bindCtx.close();
            }
            // 处理遗嘱信息
            if (mqttConnectMessage.variableHeader().isWillFlag()) {
                MqttMessage willMqttMessage = MqttMessageFactory.newMessage(
                        new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.valueOf(mqttConnectMessage.variableHeader().willQos()), mqttConnectMessage.variableHeader().isWillRetain(), 0),
                        new MqttPublishVariableHeader(mqttConnectMessage.payload().willTopic(), 0), ByteBufAllocator.DEFAULT.buffer().writeBytes(mqttConnectMessage.payload().willMessageInBytes()));
                // @todo 保存遗嘱消息到缓存
                repository().saveWillMessage(comboIdentity, willMqttMessage);
            }
            // sessionPresent
            boolean sessionPresent = cacheLoginClientInfo != null && !mqttConnectMessage.variableHeader().isCleanSession();
            // 绑定信息
            ClientHelper.bind(ctx, loginContent, loginTimestamp);
            // 处理回调信息
            Consumer<Channel> channelConsumer = channel -> {
                log.warn("客户端断开连接, 触发回调, comboIdentity: {}, channel: {}", comboIdentity, channel);
                // 处理掉线事件
                //1,从channel中的attrMap取出相关属性
                final LoginClientInfo closingLocalloginClientInfo = ChannelAttrUtil.getChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
                if (closingLocalloginClientInfo != null) {
                    // 这里不进行判空了，到这里肯定不为空（登录信息里面一定要有登录设备的类型）
                    String clientLoginDeviceName = closingLocalloginClientInfo.getDeviceType().getDeviceTypeName();
                    String closingComboIdentity = IdentityUtil.generalComboIdentity(closingLocalloginClientInfo.getAppKey(), closingLocalloginClientInfo.getIdentity(), clientLoginDeviceName);
                    // 登录信息一致,才进行解绑，删除缓存信息
                    MessageServerContext.localClientRegisterTable.delete(closingComboIdentity);
                    String loginClientInfoCacheKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + closingLocalloginClientInfo.getAppKey() + CacheConstant.COLON + CacheConstant.LOGIN + CacheConstant.USER + closingComboIdentity;
                    // 获取分布式锁, 这里使用锁的目的，可以参考登录处理器的分布式锁，防止重复解绑 LoginMessageProcessor
                    RLock lock = MessageServerContext.redissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + closingLocalloginClientInfo.getAppKey() + CacheConstant.COLON + closingComboIdentity);
                    try {
                        if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                            LoginClientInfo closingRemoteLoginClientInfo = MessageServerContext.remoteLoginClientInfoCache.get(loginClientInfoCacheKey);
                            // 这里比较两个登录服务器地址是否一致的目的是因为，无论集群还是单服务 在ctx异步关闭时,有可能存在关闭的执行顺序比绑定客户端的方法执行的慢，导致缓存被覆盖，结果又给删除了缓存信息，导致数据错乱。
                            if (closingRemoteLoginClientInfo != null && closingLocalloginClientInfo.getLoginServerAddress().equals(closingRemoteLoginClientInfo.getLoginServerAddress()) && closingRemoteLoginClientInfo.getLastLoginTime() == closingLocalloginClientInfo.getLastLoginTime()) {
                                // 缓存中有没有登录信息都进行设置下线
                                closingRemoteLoginClientInfo.setOnlineStatus(OnlineEnum.OFFLINE);
                                MessageServerContext.remoteLoginClientInfoCache.put(loginClientInfoCacheKey, closingRemoteLoginClientInfo, closingRemoteLoginClientInfo.getSessionExpiryInterval(), TimeUnit.SECONDS);
                            }else {
                                log.warn("mqtt客户端: {} 解绑登录信息失败,原因：缓存中不存在登录信息或登录地址不匹配", closingLocalloginClientInfo);
                            }
                        }else {
                            log.error("mqtt客户端: {} 绑定登录信息失败,原因：获取分布式锁失败", closingLocalloginClientInfo);
                        }
                    } catch (Exception e) {
                        log.error("mqtt客户端: {} 绑定登录信息失败,原因：{}", closingLocalloginClientInfo, e.getMessage());
                        throw new MessageException(e);
                    } finally {
                        if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                    }
                    // 发送客户端离线事件， 可以处理发送遗嘱等客户端关闭后的操作逻辑
                    MessageServerContext.publishEvent(new ClientLogoutEvent(closingLocalloginClientInfo), true);
                }
            };
            // 设置channel关闭后的钩子
            ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_CHANNEL_CLOSE_HOOK, channelConsumer);
            // 判断是否加入读写空闲,只要服务端开启支持心跳，才会可能加入心跳处理，这里可以根据自己的协议或业务逻辑进行调整
            Integer heartbeatExpireTime = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_HEARTBEAT_TIMEOUT);
            if (MessageServerContext.serverProperties().isClientHeartBeatEnable() && heartbeatExpireTime != null) {
                // 判断是否开启客户端心跳
                ctx.pipeline()
                        // 客户端登录保活处理器
                        .addAfter(MessageConstant.CONVERT_2_PACKET_HANDLER, MessageConstant.CLIENT_LOGIN_KEEP_ALIVE_HANDLER, new LoginKeepAliveHandler())
                        // 添加读写空闲处理器， 添加后，下条消息就可以接收心跳消息了
                        .addAfter(MessageConstant.CLIENT_LOGIN_KEEP_ALIVE_HANDLER, MessageConstant.HEART_BEAT_IDLE_HANDLER, new IdleStateHandler(heartbeatExpireTime, MessageConstant.ZERO, MessageConstant.ZERO))
                        // 处理心跳的以及相关逻辑都放在这里处理
                        .addAfter(MessageConstant.HEART_BEAT_IDLE_HANDLER, MessageConstant.HEART_BEAT_HANDLER, new HeartBeatHandler());
            }
            // 发送成功给到客户端
            MqttMessage mqttConnAckMessage = MqttMessageFactory.newMessage(
                    new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                    new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_ACCEPTED, sessionPresent), null);
            ctx.writeAndFlush(mqttConnAckMessage);
            log.debug("CONNECT - clientId: {}, cleanSession: {}", mqttConnectMessage.payload().clientIdentifier(), mqttConnectMessage.variableHeader().isCleanSession());
            // 如果cleanSession为0, 需要重发同一clientId存储的未完成的QoS1和QoS2的DUP消息
//            if (!msg.variableHeader().isCleanSession()) {
//                List<DupPublishMessageStore> dupPublishMessageStoreList = dupPublishMessageStoreService.get(msg.payload().clientIdentifier());
//                List<DupPubRelMessageStore> dupPubRelMessageStoreList = dupPubRelMessageStoreService.get(msg.payload().clientIdentifier());
//                dupPublishMessageStoreList.forEach(dupPublishMessageStore -> {
//                    MqttPublishMessage publishMessage = (MqttPublishMessage) MqttMessageFactory.newMessage(
//                            new MqttFixedHeader(MqttMessageType.PUBLISH, true, MqttQoS.valueOf(dupPublishMessageStore.getMqttQoS()), false, 0),
//                            new MqttPublishVariableHeader(dupPublishMessageStore.getTopic(), dupPublishMessageStore.getMessageId()), Unpooled.buffer().writeBytes(dupPublishMessageStore.getMessageBytes()));
//                    ctx.writeAndFlush(publishMessage);
//                });
//                dupPubRelMessageStoreList.forEach(dupPubRelMessageStore -> {
//                    MqttMessage pubRelMessage = MqttMessageFactory.newMessage(
//                            new MqttFixedHeader(MqttMessageType.PUBREL, true, MqttQoS.AT_MOST_ONCE, false, 0),
//                            MqttMessageIdVariableHeader.from(dupPubRelMessageStore.getMessageId()), null);
//                    ctx.writeAndFlush(pubRelMessage);
//                });
//            }
        }else {
            log.error("mqtt 非法连接connect 消息！");
        }
    }

    /***
     * @author fzx
     * @description 校验登录信息
     */
    public boolean validate(LoginContent loginContent) {
        return true;
    }
}
