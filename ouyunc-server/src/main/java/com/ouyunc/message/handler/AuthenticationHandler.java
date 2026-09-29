package com.ouyunc.message.handler;

import com.ouyunc.core.exception.ExceptionReporter;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Protocol;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.packet.message.content.ServerNotifyContent;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.device.DeviceTypeRegistry;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ClientLoginEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.LoginSessionDirectoryHelper;
import com.ouyunc.message.helper.MessageSender;
import com.ouyunc.message.protocol.NativePacketProtocol;
import com.ouyunc.message.schedule.ScheduleTimer;
import com.ouyunc.message.validator.AppKeyValidator;
import com.ouyunc.message.validator.LoginAuthValidator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @Author fzx
 * @Description: ç»å½è®¤è¯å¤çå¨
 **/
public class AuthenticationHandler extends SimpleChannelInboundHandler<Packet> {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationHandler.class);


    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description ç»å½é»è¾å¤ç
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        // å¨è¿éåä¸æ¬¡è®¾å¤çç»å½æ¯ææ ¡éªï¼å¦æä¸æ³å¨è¿æ ¡éªå¯ä»¥ä¸æ¾å°processorä¸­æ¥æ ¹æ®ä¸åçæ ¡éªå¨åæ ¡éª
        LoginContent loginInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        // ç»å½æ¶æ¯
        if (MessageTypeEnum.LOGIN.getType().equals(packet.getMessageType())) {
            if (loginInfo != null
                    || Boolean.TRUE.equals(ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT))) {
                log.warn("éå¤ç»å½åå¿½ç¥ï¼ä¸å³é­å·²ç»å®/ç»å½ä¸­çè¿æ¥ channelId={}", ctx.channel().id().asShortText());
                return;
            }
            doLogin(ctx, packet);
            return;
        }
        // éç»å½æ¶æ¯ï¼å·²ç»ç»å½æ¾è¡
        if (loginInfo == null) {
            log.warn("è¯·åç»å½!");
            ctx.close();
            return;
        }
        ctx.fireChannelRead(packet);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        LoginTimeoutSupport.install(ctx);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        boolean cancelled = LoginTimeoutSupport.cancel(ctx);
        if (cancelled) {
            log.debug("å®¢æ·ç«¯: {} è¿æ¥å³é­ï¼å·²åæ¶ç»å½è¶æ¶å®æ¶ä»»å¡", ctx.channel().id().asShortText());
        }
        super.channelInactive(ctx);
    }

    /**
     * ç»å½
     * @param ctx
     * @param packet
     */
    private void doLogin(ChannelHandlerContext ctx, Packet packet) {
        // æé é»è®¤åéçæ¯IM çæ¶æ¯æ ¼å¼
        long loginTimestamp = TimeUtil.currentTimeMillis();
        // ååºç»å½æ¶æ¯
        Message loginMessage = packet.getMessage();
        if (loginMessage.getContentType() != MessageContentTypeEnum.LOGIN_REQUEST_CONTENT.getType()) {
            log.warn("å®¢æ·ç«¯id: {} ç»å½åå®¹ç±»å: {}ï¼æ ¡éªæªéè¿ï¼", ctx.channel().id().asShortText(), loginMessage.getContentType());
            ctx.close();
            return;
        }
        // ææµ / æç»æ°è¿æ¥ï¼æ»å¨åçº§çªå£åä¸åæ¥åæ°ç»å½
        if (!MessageServerContext.isAcceptingNewConnections()) {
            log.warn("å®¢æ·ç«¯id: {} ç»å½è¢«æç»ï¼æå¡å°æªå°±ç»ªææ­£å¨ææµ", ctx.channel().id().asShortText());
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.LOGIN_REFUSED_DRAIN, "æå¡å°æªå°±ç»ªææ­£å¨ææµï¼æç»ç»å½", "AuthenticationHandler", packet);
            ctx.close();
            return;
        }
        byte deviceType = packet.getDeviceType();
        if (Boolean.TRUE.equals(ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT))) {
            log.warn("å®¢æ·ç«¯id: {} ç»å½è¿è¡ä¸­ï¼å¿½ç¥éå¤ç»å½å", ctx.channel().id().asShortText());
            return;
        }
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT, Boolean.TRUE);
        try {
            ThreadPoolManager.messageProcessorExecutor().execute(() ->
                    authenticateAndBind(ctx, packet, deviceType, loginTimestamp));
        } catch (RejectedExecutionException ex) {
            log.error("ç»å½ä»»å¡æäº¤è¢«æç» channelId={}", ctx.channel().id().asShortText(), ex);
            ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT, null);
            ctx.close();
        }
    }

    /**
     * AppKey éé¢ãè®¾å¤ç½ååãç­¾åãç»å½ GETãè¸¢äººå¨é¨ç¦»å¼ EventLoopã
     * JSON è§£æä¹å¨ä¸å¡çº¿ç¨ï¼é¿åå  EventLoopã
     * <p>è®¾å¤ç±»åèµ°è½¯æ ¡éªï¼ä¸ PacketHandler è®¾å¤ç½ååä¸è´ï¼ï¼ä¸æå¼å¸¸ã</p>
     */
    private void authenticateAndBind(ChannelHandlerContext ctx, Packet packet,
                                     byte deviceType, long loginTimestamp) {
        Message loginMessage = packet.getMessage();
        LoginContent loginContent = null;
        try {
            if (!ctx.channel().isActive()) {
                ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT, null);
                return;
            }
            loginContent = JSON.parseObject(loginMessage.getContent(), LoginContent.class);
            if (loginContent == null) {
                log.warn("å®¢æ·ç«¯id: {} ç»å½åå®¹æ æ³è§£æ", ctx.channel().id().asShortText());
                failLoginOnEventLoop(ctx);
                return;
            }
            loginContent.setScope(LoginScopeEnum.normalizeScope(loginContent.getScope()));
            // identity çº§ç½ååä¼åï¼æ å®å¶æ¶ç­ä»·äº appKey/å¨å±ç½åå
            if (!AppKeyValidator.INSTANCE.tryReserveForLogin(loginContent.getAppKey(), ctx)
                    || !DeviceTypeRegistry.supports(
                    loginContent.getAppKey(), loginContent.getIdentity(), deviceType)
                    || !validate(loginContent)) {
                log.warn("å®¢æ·ç«¯id: {} ç»å½åæ°: {}ï¼æ ¡éªæªéè¿ï¼",
                        ctx.channel().id().asShortText(), Serializer.JSON.serializeToString(loginContent));
                ExceptionReporter.reportBusiness(ExceptionCodeEnum.LOGIN_VERIFY_ERROR, "ç»å½æ ¡éªæªéè¿", "AuthenticationHandler", packet);
                AppKeyValidator.releaseReservedIfNeeded(loginContent.getAppKey(), ctx);
                failLoginOnEventLoop(ctx);
                return;
            }
            Protocol protocol = ctx.channel().attr(NativePacketProtocol.protocolAttrKey).get();
            if (protocol == null) {
                log.warn("Protocol not set on channel, closing connection: {}", ctx.channel().id().asShortText());
                AppKeyValidator.releaseReservedIfNeeded(loginContent.getAppKey(), ctx);
                failLoginOnEventLoop(ctx);
                return;
            }
            LoginClientInfo loginClientInfo = new LoginClientInfo(
                    protocol.getProtocol(), protocol.getProtocolVersion(),
                    MessageContext.messageProperties.getLocalServerAddress(), OnlineEnum.ONLINE, null,
                    ClientHelper.calculateClientHeartBeatTimeout(loginContent.getHeartBeatExpireTime()),
                    loginTimestamp, loginContent.getAppKey(), loginContent.getIdentity(), deviceType,
                    loginContent.getSupportDeviceTypes(), loginContent.getSn(), loginContent.getSignature(),
                    loginContent.getSignatureAlgorithm(), loginContent.getHeartBeatExpireTime(), loginContent.getCreateTime(),
                    loginContent.getEnableWill(), loginContent.getWillMessage(), loginContent.getEnableAlive(),
                    loginContent.getAliveMessage(), loginContent.getScope(), loginContent.getBusinessIdleSeconds(),
                    loginContent.getHeartBeatWaitRetry(), loginContent.getBusinessIdleCloseStrike());
            try {
                ctx.executor().execute(() -> startRemoteBind(ctx, packet, loginClientInfo));
            } catch (RejectedExecutionException scheduleError) {
                log.error("ç»å½ç»å®åè°æé EventLoop è¢«æç» channelId={}", ctx.channel().id().asShortText(), scheduleError);
                AppKeyValidator.releaseReservedIfNeeded(loginContent.getAppKey(), ctx);
                failLoginOnEventLoop(ctx);
            }
        } catch (Exception e) {
            log.error("ç»å½æ ¡éªå¼å¸¸ channelId={}", ctx.channel().id().asShortText(), e);
            if (loginContent != null) {
                AppKeyValidator.releaseReservedIfNeeded(loginContent.getAppKey(), ctx);
            }
            failLoginOnEventLoop(ctx);
        }
    }

    private void startRemoteBind(ChannelHandlerContext ctx, Packet packet,
                                 LoginClientInfo loginClientInfo) {
        if (!ctx.channel().isActive()) {
            ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT, null);
            AppKeyValidator.releaseReservedIfNeeded(loginClientInfo.getAppKey(), ctx);
            return;
        }
        Consumer<Channel> channelCloseHook = channel -> {
            LoginClientInfo attrLogin = ChannelAttrUtil.getChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
            LoginClientInfo closingLogin = attrLogin != null ? attrLogin : loginClientInfo;
            String closingComboIdentity = IdentityUtil.generalComboIdentity(
                    closingLogin.getAppKey(), closingLogin.getIdentity(), closingLogin.getDeviceType());
            ClientHelper.unregisterLocal(closingComboIdentity, channel, closingLogin.getAppKey());
            AppKeyValidator.releaseReservedIfNeeded(closingLogin.getAppKey(), channel);
            final boolean publishLogout = attrLogin != null;
            ThreadPoolManager.messageProcessorExecutor().execute(() ->
                    unbindRemoteOnClose(packet, closingLogin, closingComboIdentity, publishLogout));
        };
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_CHANNEL_CLOSE_HOOK, channelCloseHook);
        // è¸¢æ§ä¼è¯å¿é¡»å¨ CAS ç»å®èåºä¹åï¼é¿åéå¤è¸¢äººå¯¼è´è·¨èç¹åå¨çº¿çªå£
        ClientHelper.bindAsync(ctx, loginClientInfo).whenComplete((previous, ex) -> {
            try {
                // fencing GET / è¸¢äººç¦æ­¢åå° EventLoopï¼whenComplete å¯è½å·²å¨ IO çº¿ç¨
                ThreadPoolManager.messageProcessorExecutor().execute(() ->
                        finishLoginAfterDirectoryCheck(ctx, packet, loginClientInfo, previous, ex));
            } catch (RejectedExecutionException scheduleError) {
                log.error("ç»å½ fencing æéä¸å¡çº¿ç¨è¢«æç» channelId={}", ctx.channel().id().asShortText(), scheduleError);
                ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT, null);
                AppKeyValidator.releaseReservedIfNeeded(loginClientInfo.getAppKey(), ctx);
                if (ctx.channel().isActive()) {
                    ctx.channel().close();
                }
            }
        });
    }

    /**
     * Redis ç®å½ fencing ä¸è·¨èç¹è¸¢äººå¨ä¸å¡çº¿ç¨å®æï¼åå EventLoop è£ç®¡é/å ACKã
     */
    private void finishLoginAfterDirectoryCheck(ChannelHandlerContext ctx, Packet packet,
                                                LoginClientInfo loginClientInfo,
                                                LoginClientInfo previous, Throwable bindError) {
        boolean directoryOwned = false;
        if (bindError == null && ctx.channel().isActive()) {
            directoryOwned = ClientHelper.stillOwnsDirectory(loginClientInfo);
            if (directoryOwned) {
                kickPreviousSessionAfterBindWin(packet, loginClientInfo, previous);
                directoryOwned = ClientHelper.stillOwnsDirectory(loginClientInfo);
            }
        }
        final boolean owned = directoryOwned;
        try {
            ctx.executor().execute(() -> completeLoginAfterRemoteBind(
                    ctx, packet, loginClientInfo, bindError, owned));
        } catch (RejectedExecutionException scheduleError) {
            log.error("ç»å½å®æåè°æé EventLoop è¢«æç» channelId={}", ctx.channel().id().asShortText(), scheduleError);
            ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT, null);
            AppKeyValidator.releaseReservedIfNeeded(loginClientInfo.getAppKey(), ctx);
            if (ctx.channel().isActive()) {
                ctx.channel().close();
            }
        }
    }

    private void failLoginOnEventLoop(ChannelHandlerContext ctx) {
        Runnable fail = () -> {
            ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT, null);
            ctx.close();
        };
        try {
            ctx.executor().execute(fail);
        } catch (RejectedExecutionException scheduleError) {
            log.error("ç»å½å¤±è´¥åè°æé EventLoop è¢«æç» channelId={}", ctx.channel().id().asShortText(), scheduleError);
            fail.run();
        }
    }

    /**
     * closeFuture å¨ EventLoop ä¸è§¦åï¼Redis è§£ç»å¿é¡»ç¦»å¼ IO çº¿ç¨ã
     */
    private void unbindRemoteOnClose(Packet packet, LoginClientInfo closingLogin, String comboIdentity, boolean publishLogout) {
        boolean locked = tryUnbindMatchingSession(closingLogin, comboIdentity);
        if (!locked) {
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.UN_BIND_ERROR, "å®¢æ·ç«¯è§£ç»ç»å½ä¿¡æ¯å¤±è´¥ï¼è·ååå¸å¼éå¤±è´¥", "AuthenticationHandler", packet);
            ScheduleTimer.scheduleOnce(() -> {
                if (!tryUnbindMatchingSession(closingLogin, comboIdentity)) {
                    log.error("è§£ç»è¡¥å¿ä»å¤±è´¥ï¼ç­å¾ä¸æ¬¡ç»å½æèç¹ç§çº¦è¿æ combo={}", comboIdentity);
                }
            }, MessageConstant.UNBIND_COMPENSATE_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        }
        if (publishLogout) {
            MessageServerContext.publishEvent(new MessageEvent(closingLogin, MessageEventTypeEnum.CLIENT_LOGOUT), true);
        }
    }

    private static boolean tryUnbindMatchingSession(LoginClientInfo closingLogin, String comboIdentity) {
        return ClientHelper.tryRunWithBindLock(closingLogin.getAppKey(), comboIdentity, () -> {
            if (ClientHelper.stillOwnsDirectory(closingLogin)) {
                LoginSessionDirectoryHelper.unbind(closingLogin);
                ClientHelper.invalidateRouteCacheEverywhere(closingLogin);
            }
        });
    }

    /**
     * CAS ç»å®èåºåè¸¢æ§ä¼è¯ï¼å sn ä»éé»æ­å¼ï¼å¼ sn åè¿ç¨ç»å½éç¥åæ­å¼ï¼å«è·¨èç¹ï¼ã
     * æ¬æºæ§è¿æ¥å·²å¨ {@link ClientHelper#bindAsync} æ³¨åæ¶å³é­ï¼æ­¤å¤ä¸»è¦å¤çè·¨èç¹æ§ä¼è¯ã
     */
    private void kickPreviousSessionAfterBindWin(Packet packet, LoginClientInfo loginClientInfo,
                                                 LoginClientInfo previous) {
        if (previous == null) {
            return;
        }
        String local = MessageContext.messageProperties.getLocalServerAddress();
        // æ¬æºæ§è¿æ¥å·²å¨ bindAsync æ³¨åæ¶å³é­ï¼å¿åæ identity æ¬æºæéï¼å¦åä¼è¯¯è¸¢æ°ä¼è¯
        if (StringUtils.isNotBlank(previous.getLoginServerAddress())
                && previous.getLoginServerAddress().equals(local)) {
            return;
        }
        boolean sameDevice = StringUtils.isNotBlank(previous.getSn())
                && StringUtils.isNotBlank(loginClientInfo.getSn())
                && previous.getSn().equals(loginClientInfo.getSn());
        if (sameDevice) {
            closePreviousRemoteQuietly(previous);
            return;
        }
        Message kickMessage = new Message(
                MessageContext.idGenerator().generateIdStr(),
                null,
                loginClientInfo.getIdentity(),
                MessageContentTypeEnum.REMOTE_LOGIN_CONTENT.getType(),
                Serializer.JSON.serializeToString(new ServerNotifyContent(
                        String.format(MessageConstant.REMOTE_LOGIN_NOTIFICATIONS,
                                packet.getMessage().getMetadata().getIngress().getClientIp()))),
                loginClientInfo.getLastLoginTime(),
                packet.getMessage().getMetadata());
        Packet kickPacket = new Packet(
                packet.getProtocol(),
                packet.getProtocolVersion(),
                MessageContext.idGenerator().generateId(),
                previous.getDeviceType(),
                NetworkEnum.OTHER.getValue(),
                packet.getEncryptType(),
                packet.getSerializeAlgorithm(),
                MessageTypeEnum.SERVER_NOTIFY.getType(),
                kickMessage);
        Target kickTarget = Target.newBuilder()
                .appKey(previous.getAppKey())
                .targetIdentity(previous.getIdentity())
                .targetServerAddress(previous.getLoginServerAddress())
                .deviceType(previous.getDeviceType())
                .build();
        MessageSender.send(kickPacket, kickTarget);
    }

    /**
     * å sn é¡¶å·ï¼åæ§ä¼è¯æå¨èç¹æéå³é­éç¥ãæ¬æºæ§è¿æ¥å·²å¨ bind æ¶å³é­ã
     */
    private void closePreviousRemoteQuietly(LoginClientInfo previous) {
        if (previous == null || StringUtils.isBlank(previous.getLoginServerAddress())) {
            return;
        }
        String local = MessageContext.messageProperties.getLocalServerAddress();
        if (previous.getLoginServerAddress().equals(local)) {
            return;
        }
        try {
            Message kickMessage = new Message(
                    MessageContext.idGenerator().generateIdStr(),
                    null,
                    previous.getIdentity(),
                    MessageContentTypeEnum.REMOTE_LOGIN_CONTENT.getType(),
                    Serializer.JSON.serializeToString(new ServerNotifyContent(
                            MessageConstant.REMOTE_LOGIN_SAME_DEVICE_KICK)),
                    TimeUtil.currentTimeMillis(),
                    null);
            Packet kickPacket = new Packet(
                    previous.getProtocol(),
                    previous.getProtocolVersion(),
                    MessageContext.idGenerator().generateId(),
                    previous.getDeviceType(),
                    NetworkEnum.OTHER.getValue(),
                    NumberConstant.NUMBER_0,
                    Serializer.JSON.getValue(),
                    MessageTypeEnum.SERVER_NOTIFY.getType(),
                    kickMessage);
            Target kickTarget = Target.newBuilder()
                    .appKey(previous.getAppKey())
                    .targetIdentity(previous.getIdentity())
                    .targetServerAddress(previous.getLoginServerAddress())
                    .deviceType(previous.getDeviceType())
                    .build();
            MessageSender.send(kickPacket, kickTarget);
        } catch (Exception e) {
            log.warn("åè®¾å¤è·¨èç¹éé»è¸¢æ§å¤±è´¥ identity={}: {}", previous.getIdentity(), e.getMessage());
        }
    }

    /**
     * å¨ EventLoop ä¸å®æç»å½ ACK ä¸ç®¡éå®è£ãç®å½ fencing / è¸¢äººå·²å¨ä¸å¡çº¿ç¨åå®ã
     */
    private void completeLoginAfterRemoteBind(ChannelHandlerContext ctx, Packet packet,
                                              LoginClientInfo loginClientInfo, Throwable bindError,
                                              boolean directoryOwned) {
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT, null);
        if (!ctx.channel().isActive()) {
            ClientHelper.unbindLocalRegisterTable(loginClientInfo, ctx);
            AppKeyValidator.releaseReservedIfNeeded(loginClientInfo.getAppKey(), ctx);
            return;
        }
        if (bindError != null) {
            log.error("å®¢æ·ç«¯: {} ç»å½ç»å®å¤±è´¥", loginClientInfo, bindError);
            ExceptionReporter.reportSystem(ExceptionCodeEnum.LOGIN_VERIFY_ERROR,
                    "ç»å½ç»å®å¤±è´¥: " + bindError.getMessage(),
                    "AuthenticationHandler.finishLoginBind", packet, bindError);
            ClientHelper.unbindLocalRegisterTable(loginClientInfo, ctx);
            AppKeyValidator.releaseReservedIfNeeded(loginClientInfo.getAppKey(), ctx);
            ctx.close();
            return;
        }
        if (!directoryOwned) {
            log.warn("ç»å½ fencing å¤±è´¥ï¼ç®å½å·²è¢«æ´æ°ä¼è¯è¦ç identity={}", loginClientInfo.getIdentity());
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.LOGIN_VERIFY_ERROR, "ç»å½ç»å®å¤±è´¥ï¼ä¼è¯å·²è¢«æ´æ°è¿æ¥é¡¶æ¿", "AuthenticationHandler", packet);
            ClientHelper.unbindLocalRegisterTable(loginClientInfo, ctx);
            AppKeyValidator.releaseReservedIfNeeded(loginClientInfo.getAppKey(), ctx);
            ctx.close();
            return;
        }
        installLoginIdlePipeline(ctx, loginClientInfo);
        Message loginAckMessage = new Message(
                MessageContext.idGenerator().generateIdStr(),
                null,
                loginClientInfo.getIdentity(),
                MessageContentTypeEnum.LOGIN_RESPONSE_SUCCESS_CONTENT.getType(),
                MessageContentTypeEnum.LOGIN_RESPONSE_SUCCESS_CONTENT.getDescription(),
                loginClientInfo.getLastLoginTime(),
                packet.getMessage().getMetadata());
        Packet loginAckPacket = new Packet(
                packet.getProtocol(),
                packet.getProtocolVersion(),
                MessageContext.idGenerator().generateId(),
                loginClientInfo.getDeviceType(),
                packet.getNetworkType(),
                packet.getEncryptType(),
                packet.getSerializeAlgorithm(),
                MessageTypeEnum.LOGIN.getType(),
                loginAckMessage);
        MessageSender.sendControl(ctx, loginAckPacket);
        if (!LoginTimeoutSupport.cancel(ctx)) {
            log.warn("å®¢æ·ç«¯: {} ç»å½æåï¼åæ¶ç»å½è¶æ¶å®æ¶ä»»å¡å¤±è´¥", loginClientInfo);
        }
        publishClientLoginOutsideEventLoop(ctx, loginClientInfo, loginClientInfo.getLastLoginTime());
    }

    /**
     * ç»å½äºä»¶å¿é¡»å¯é è¿å¥ Disruptorï¼ä½ç¯æ»¡æ¶ {@code publishEvent} ä¼ç­å¾å¯ç¨æ§½ä½ã
     * å½åæ¹æ³åªå¨ EventLoop ä¸æ§è¡ä¸æ¬¡æççº¿ç¨æ± æäº¤ï¼é¿åç»å½æ´ªå³°ææ¢çå¬å¨ååæ´ä¸ªç½ç»çº¿ç¨ã
     * å¦æäºä»¶çº¿ç¨æ± å·²ç»è¿è½½ï¼åå³é­åå»ºç«çè¿æ¥ï¼è®©å®¢æ·ç«¯éæ°ç»å½æ¢å¤å®æ´çä¸çº¿äºä»¶è¯­ä¹ã
     */
    private void publishClientLoginOutsideEventLoop(ChannelHandlerContext ctx,
                                                    LoginClientInfo loginClientInfo,
                                                    long loginTimestamp) {
        MessageEvent loginEvent = new MessageEvent(
                new ClientLoginEventPayload(loginClientInfo, ctx),
                MessageEventTypeEnum.CLIENT_LOGIN,
                loginTimestamp);
        try {
            ThreadPoolManager.eventListenerExecutor().execute(
                    () -> {
                        // ä»»å¡æéæé´è¿æ¥å¯è½å·²ç»å³é­ï¼æ­¤æ¶ closeFuture ä¼åå¸ç»åºäºä»¶ï¼ç¦æ­¢åè¡¥åä¸çº¿äºä»¶ã
                        if (ctx.channel().isActive()) {
                            MessageServerContext.publishEvent(loginEvent, true);
                        }
                    });
        } catch (RejectedExecutionException rejected) {
            log.error("ç»å½äºä»¶æäº¤è¢«æç»ï¼å³é­è¿æ¥ç­å¾å®¢æ·ç«¯éè¯ identity={}, channelId={}",
                    loginClientInfo.getIdentity(), ctx.channel().id().asShortText(), rejected);
            ctx.close();
        }
    }

    /**
     * ç»å½æååå®è£ç®¡éï¼å¿è·³è¯»ç©ºé²ï¼ç¬¬ä¸ä¸ª {@link IdleStateHandler} + {@link HeartBeatHandler}ï¼å¯éï¼ï¼
     * ä¸å¡è¯»ç©ºé²ä¸º {@link BusinessIdleStateHandler}ï¼ç»§æ¿ {@link IdleStateHandler}ï¼åå¹¶ PING ä¸è¯»ç©ºé²äºä»¶å¤çï¼å°ä¸å± handlerï¼ã
     */
    private void installLoginIdlePipeline(ChannelHandlerContext ctx, LoginClientInfo loginClientInfo) {
        String pipelineAnchor = MessageConstant.CONVERT_2_PACKET_HANDLER;
        Integer heartbeatExpireTime = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_HEARTBEAT_TIMEOUT);
        boolean heartbeatInstalled = MessageServerContext.serverProperties().isClientHeartBeatEnable() && heartbeatExpireTime != null;
        if (heartbeatInstalled) {
            if (loginClientInfo.getHeartBeatWaitRetry() > 0) {
                ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_HEARTBEAT_WAIT_RETRY,
                        loginClientInfo.getHeartBeatWaitRetry());
            }
            ctx.pipeline()
                    .addAfter(pipelineAnchor, MessageConstant.HEART_BEAT_IDLE_HANDLER, new IdleStateHandler(heartbeatExpireTime, NumberConstant.NUMBER_0, NumberConstant.NUMBER_0, TimeUnit.SECONDS))
                    .addAfter(MessageConstant.HEART_BEAT_IDLE_HANDLER, MessageConstant.HEART_BEAT_HANDLER, new HeartBeatHandler());
            pipelineAnchor = MessageConstant.HEART_BEAT_HANDLER;
        }
        if (loginClientInfo.getBusinessIdleSeconds() <= 0) {
            return;
        }
        int bizSec = loginClientInfo.getBusinessIdleSeconds();
        ctx.pipeline().addAfter(pipelineAnchor, MessageConstant.BUSINESS_READ_IDLE_HANDLER, new BusinessIdleStateHandler(bizSec));
    }

    /***
     * @author fzx
     * @description æ ¡éªç»å½ä¿¡æ¯ï¼{@code scope} å¿é¡»ä¸º {@link LoginScopeEnum} å·²å®ä¹åå¼ï¼
     * identity å¨å®¢æ scope ä¸é¡»å¨ {@code ouyunc_im_user} å­å¨ä¸å±äºè¯¥ appKeyï¼
     * ç­¾åä¸º {@code MD5(appKey&identity&createTime_appSecret)}ï¼createTime åè®¸
     * {@link MessageConstant#LOGIN_SIGNATURE_CREATE_TIME_SKEW_MS} åå·®ã
     */
    public boolean validate(LoginContent loginContent) {
        return LoginAuthValidator.verify(loginContent);
    }
}
