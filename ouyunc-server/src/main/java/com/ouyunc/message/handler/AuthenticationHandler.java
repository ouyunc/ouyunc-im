package com.ouyunc.message.handler;

import com.ouyunc.core.exception.ExceptionReporter;

import com.alibaba.fastjson2.JSON;
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
 * @Description: 登录认证处理器
 **/
public class AuthenticationHandler extends SimpleChannelInboundHandler<Packet> {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationHandler.class);


    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 登录逻辑处理
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        // 在这里做一次设备的登录支持校验，如果不想在这校验可以下放到processor中来根据不同的校验器做校验
        LoginContent loginInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        // 登录消息
        if (MessageTypeEnum.LOGIN.getType().equals(packet.getMessageType())) {
            if (loginInfo != null
                    || Boolean.TRUE.equals(ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT))) {
                log.warn("重复登录包忽略，不关闭已绑定/登录中的连接 channelId={}", ctx.channel().id().asShortText());
                return;
            }
            doLogin(ctx, packet);
            return;
        }
        // 非登录消息，已经登录放行
        if (loginInfo == null) {
            log.warn("请先登录!");
            failLogin(ctx, packet, "请先登录");
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
            log.debug("客户端: {} 连接关闭，已取消登录超时定时任务", ctx.channel().id().asShortText());
        }
        super.channelInactive(ctx);
    }

    /**
     * 登录
     * @param ctx
     * @param packet
     */
    private void doLogin(ChannelHandlerContext ctx, Packet packet) {
        // 构造默认发送的是IM 的消息格式
        long loginTimestamp = TimeUtil.currentTimeMillis();
        // 取出登录消息
        Message loginMessage = packet.getMessage();
        if (loginMessage.getContentType() != MessageContentTypeEnum.LOGIN_REQUEST_CONTENT.getType()) {
            log.warn("客户端id: {} 登录内容类型: {}，校验未通过！", ctx.channel().id().asShortText(), loginMessage.getContentType());
            failLogin(ctx, packet, "登录内容类型不正确");
            return;
        }
        // 摘流 / 拒绝新连接：滚动升级窗口内不再接受新登录
        if (!MessageServerContext.isAcceptingNewConnections()) {
            log.warn("客户端id: {} 登录被拒绝：服务尚未就绪或正在摘流", ctx.channel().id().asShortText());
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.LOGIN_REFUSED_DRAIN, "服务尚未就绪或正在摘流，拒绝登录", "AuthenticationHandler", packet);
            failLogin(ctx, packet, "服务尚未就绪或正在摘流");
            return;
        }
        byte deviceType = packet.getDeviceType();
        if (Boolean.TRUE.equals(ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT))) {
            log.warn("客户端id: {} 登录进行中，忽略重复登录包", ctx.channel().id().asShortText());
            return;
        }
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT, Boolean.TRUE);
        try {
            ThreadPoolManager.messageProcessorExecutor().execute(() ->
                    authenticateAndBind(ctx, packet, deviceType, loginTimestamp));
        } catch (RejectedExecutionException ex) {
            log.error("登录任务提交被拒绝 channelId={}", ctx.channel().id().asShortText(), ex);
            failLogin(ctx, packet, "登录任务繁忙，请重试");
        }
    }

    /**
     * AppKey 配额、设备白名单、签名、登录 GET、踢人全部离开 EventLoop。
     * JSON 解析也在业务线程，避免占 EventLoop。
     * <p>设备类型走软校验（与 PacketHandler 设备白名单一致），不抛异常。</p>
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
                log.warn("客户端id: {} 登录内容无法解析", ctx.channel().id().asShortText());
                failLogin(ctx, packet, "登录内容无法解析");
                return;
            }
            loginContent.setScope(LoginScopeEnum.normalizeScope(loginContent.getScope()));
            // identity 级白名单优先；无定制时等价于 appKey/全局白名单
            if (!AppKeyValidator.INSTANCE.tryReserveForLogin(loginContent.getAppKey(), ctx)
                    || !DeviceTypeRegistry.supports(
                    loginContent.getAppKey(), loginContent.getIdentity(), deviceType)
                    || !validate(loginContent)) {
                log.warn("客户端id: {} 登录参数: {}，校验未通过！",
                        ctx.channel().id().asShortText(), Serializer.JSON.serializeToString(loginContent));
                ExceptionReporter.reportBusiness(ExceptionCodeEnum.LOGIN_VERIFY_ERROR, "登录校验未通过", "AuthenticationHandler", packet);
                AppKeyValidator.releaseReservedIfNeeded(loginContent.getAppKey(), ctx);
                failLogin(ctx, packet, "登录校验未通过", loginContent.getIdentity());
                return;
            }
            Protocol protocol = ctx.channel().attr(NativePacketProtocol.protocolAttrKey).get();
            if (protocol == null) {
                log.warn("Protocol not set on channel, closing connection: {}", ctx.channel().id().asShortText());
                AppKeyValidator.releaseReservedIfNeeded(loginContent.getAppKey(), ctx);
                failLogin(ctx, packet, "连接协议未就绪", loginContent.getIdentity());
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
                log.error("登录绑定回调投递 EventLoop 被拒绝 channelId={}", ctx.channel().id().asShortText(), scheduleError);
                AppKeyValidator.releaseReservedIfNeeded(loginContent.getAppKey(), ctx);
                failLogin(ctx, packet, "登录任务繁忙，请重试", loginContent.getIdentity());
            }
        } catch (Exception e) {
            log.error("登录校验异常 channelId={}", ctx.channel().id().asShortText(), e);
            if (loginContent != null) {
                AppKeyValidator.releaseReservedIfNeeded(loginContent.getAppKey(), ctx);
            }
            failLogin(ctx, packet, "登录校验异常", loginContent == null ? null : loginContent.getIdentity());
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
            submitUnbindRemote(packet, closingLogin, closingComboIdentity, publishLogout, 0);
        };
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_CHANNEL_CLOSE_HOOK, channelCloseHook);
        // 踢旧会话必须在 CAS 绑定胜出之后，避免锁外踢人导致跨节点双在线窗口
        ClientHelper.bindAsync(ctx, loginClientInfo).whenComplete((previous, ex) -> {
            try {
                // fencing GET / 踢人禁止回到 EventLoop；whenComplete 可能已在 IO 线程
                ThreadPoolManager.messageProcessorExecutor().execute(() ->
                        finishLoginAfterDirectoryCheck(ctx, packet, loginClientInfo, previous, ex));
            } catch (RejectedExecutionException scheduleError) {
                log.error("登录 fencing 投递业务线程被拒绝 channelId={}", ctx.channel().id().asShortText(), scheduleError);
                ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT, null);
                AppKeyValidator.releaseReservedIfNeeded(loginClientInfo.getAppKey(), ctx);
                failLogin(ctx, packet, "登录任务繁忙，请重试", loginClientInfo.getIdentity());
            }
        });
    }

    /**
     * Redis 目录 fencing 与跨节点踢人在业务线程完成，再回 EventLoop 装管道/发 ACK。
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
            log.error("登录完成回调投递 EventLoop 被拒绝 channelId={}", ctx.channel().id().asShortText(), scheduleError);
            ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT, null);
            AppKeyValidator.releaseReservedIfNeeded(loginClientInfo.getAppKey(), ctx);
            failLogin(ctx, packet, "登录任务繁忙，请重试", loginClientInfo.getIdentity());
        }
    }

    /**
     * 业务登录失败：先回 LOGIN + 失败内容，写出结束后再关连接。
     * 通道已失效、重复登录忽略不走这里。回包失败时直接关闭，避免登录超时前一直占着连接。
     */
    private void failLogin(ChannelHandlerContext ctx, Packet packet, String reason) {
        failLogin(ctx, packet, reason, null);
    }

    private void failLogin(ChannelHandlerContext ctx, Packet packet, String reason, String toIdentity) {
        Runnable fail = () -> closeWithLoginFail(ctx, packet, reason, toIdentity);
        if (ctx.executor().inEventLoop()) {
            fail.run();
            return;
        }
        try {
            ctx.executor().execute(fail);
        } catch (RejectedExecutionException scheduleError) {
            log.error("登录失败回调投递 EventLoop 被拒绝 channelId={}", ctx.channel().id().asShortText(), scheduleError);
            ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT, null);
            ctx.close();
        }
    }

    private void closeWithLoginFail(ChannelHandlerContext ctx, Packet packet, String reason, String toIdentity) {
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT, null);
        if (!ctx.channel().isActive()) {
            return;
        }
        try {
            MessageSender.sendControl(ctx, buildLoginFailPacket(packet, reason, toIdentity))
                    .whenComplete((result, error) -> ctx.close());
        } catch (Exception e) {
            log.warn("登录失败回包异常，直接关闭 channelId={}", ctx.channel().id().asShortText(), e);
            ctx.close();
        }
    }

    /**
     * 失败原因放在内容里，协议和加密与入站登录包一致，便于客户端解密。
     * {@code to} 优先用已解析的登录身份，与成功回包一致。
     */
    private Packet buildLoginFailPacket(Packet packet, String reason, String toIdentity) {
        Message inbound = packet.getMessage();
        String to = StringUtils.isNotBlank(toIdentity)
                ? toIdentity
                : (inbound == null ? null : inbound.getFrom());
        Message failMessage = new Message(
                MessageContext.idGenerator().generateIdStr(),
                null,
                to,
                MessageContentTypeEnum.LOGIN_RESPONSE_FAIL_CONTENT.getType(),
                reason,
                TimeUtil.currentTimeMillis(),
                inbound == null ? null : inbound.getMetadata());
        return new Packet(
                packet.getProtocol(),
                packet.getProtocolVersion(),
                MessageContext.idGenerator().generateId(),
                packet.getDeviceType(),
                packet.getNetworkType(),
                packet.getEncryptType(),
                packet.getSerializeAlgorithm(),
                MessageTypeEnum.LOGIN.getType(),
                failMessage);
    }

    /**
     * closeFuture 在 EventLoop 上触发，Redis 解绑必须离开 IO 线程。
     */
    private void unbindRemoteOnClose(Packet packet, LoginClientInfo closingLogin, String comboIdentity, boolean publishLogout) {
        boolean locked = tryUnbindMatchingSession(closingLogin, comboIdentity);
        if (!locked) {
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.UN_BIND_ERROR, "客户端解绑登录信息失败！获取分布式锁失败", "AuthenticationHandler", packet);
            scheduleUnbindCompensation(packet, closingLogin, comboIdentity);
        }
        if (publishLogout) {
            MessageServerContext.publishEvent(new MessageEvent(closingLogin, MessageEventTypeEnum.CLIENT_LOGOUT), true);
        }
    }

    /**
     * closeFuture 运行在 EventLoop；这里只做非阻塞提交。执行器过载时由时间轮延迟重新提交，
     * 时间轮本身不获取分布式锁、不访问 Redis，避免一个慢解绑拖延全节点定时任务。
     */
    private void submitUnbindRemote(Packet packet, LoginClientInfo closingLogin, String comboIdentity,
                                    boolean publishLogout, int retry) {
        try {
            ThreadPoolManager.messageProcessorExecutor().execute(() ->
                    unbindRemoteOnClose(packet, closingLogin, comboIdentity, publishLogout));
        } catch (RejectedExecutionException rejected) {
            if (retry >= MessageConstant.UNBIND_SUBMIT_MAX_RETRIES) {
                log.error("解绑任务提交持续被拒绝，等待下次登录或节点租约过期 combo={} retries={}",
                        comboIdentity, retry, rejected);
                ExceptionReporter.reportSystem(ExceptionCodeEnum.UN_BIND_ERROR,
                        "登录目录解绑任务提交持续被拒绝", "AuthenticationHandler.submitUnbindRemote",
                        packet, rejected);
                return;
            }
            io.netty.util.Timeout timeout = ScheduleTimer.scheduleOnce(
                    () -> submitUnbindRemote(packet, closingLogin, comboIdentity, publishLogout, retry + 1),
                    MessageConstant.UNBIND_COMPENSATE_DELAY_MILLIS, TimeUnit.MILLISECONDS);
            if (timeout == null) {
                log.error("解绑任务重新提交调度失败，等待下次登录或节点租约过期 combo={}", comboIdentity);
            }
        }
    }

    /** 分布式锁未取得时延迟补偿；到期后先切换到业务执行器，再执行锁和 Redis 操作。 */
    private void scheduleUnbindCompensation(Packet packet, LoginClientInfo closingLogin, String comboIdentity) {
        io.netty.util.Timeout timeout = ScheduleTimer.scheduleOnce(
                () -> submitUnbindCompensation(packet, closingLogin, comboIdentity, 0),
                MessageConstant.UNBIND_COMPENSATE_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        if (timeout == null) {
            log.error("解绑补偿调度失败，等待下次登录或节点租约过期 combo={}", comboIdentity);
        }
    }

    private void submitUnbindCompensation(Packet packet, LoginClientInfo closingLogin,
                                          String comboIdentity, int retry) {
        try {
            ThreadPoolManager.messageProcessorExecutor().execute(() -> {
                if (!tryUnbindMatchingSession(closingLogin, comboIdentity)) {
                    log.error("解绑补偿仍失败，等待下次登录或节点租约过期 combo={}", comboIdentity);
                }
            });
        } catch (RejectedExecutionException rejected) {
            if (retry >= MessageConstant.UNBIND_SUBMIT_MAX_RETRIES) {
                log.error("解绑补偿任务提交持续被拒绝，等待下次登录或节点租约过期 combo={} retries={}",
                        comboIdentity, retry, rejected);
                ExceptionReporter.reportSystem(ExceptionCodeEnum.UN_BIND_ERROR,
                        "登录目录解绑补偿任务提交持续被拒绝", "AuthenticationHandler.submitUnbindCompensation",
                        packet, rejected);
                return;
            }
            io.netty.util.Timeout timeout = ScheduleTimer.scheduleOnce(
                    () -> submitUnbindCompensation(packet, closingLogin, comboIdentity, retry + 1),
                    MessageConstant.UNBIND_COMPENSATE_DELAY_MILLIS, TimeUnit.MILLISECONDS);
            if (timeout == null) {
                log.error("解绑补偿重新提交调度失败，等待下次登录或节点租约过期 combo={}", comboIdentity);
            }
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
     * CAS 绑定胜出后踢旧会话：同 sn 仅静默断开；异 sn 发远程登录通知后断开（含跨节点）。
     * 本机旧连接已在 {@link ClientHelper#bindAsync} 注册时关闭，此处主要处理跨节点旧会话。
     */
    private void kickPreviousSessionAfterBindWin(Packet packet, LoginClientInfo loginClientInfo,
                                                 LoginClientInfo previous) {
        if (previous == null) {
            return;
        }
        String local = MessageContext.messageProperties.getLocalServerAddress();
        // 本机旧连接已在 bindAsync 注册时关闭；勿再按 identity 本机投递，否则会误踢新会话
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
     * 同 sn 顶号：向旧会话所在节点投递关闭通知。本机旧连接已在 bind 时关闭。
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
            log.warn("同设备跨节点静默踢旧失败 identity={}: {}", previous.getIdentity(), e.getMessage());
        }
    }

    /**
     * 在 EventLoop 上完成登录 ACK 与管道安装。目录 fencing / 踢人已在业务线程做完。
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
            log.error("客户端: {} 登录绑定失败", loginClientInfo, bindError);
            ExceptionReporter.reportSystem(ExceptionCodeEnum.LOGIN_VERIFY_ERROR,
                    "登录绑定失败: " + bindError.getMessage(),
                    "AuthenticationHandler.finishLoginBind", packet, bindError);
            ClientHelper.unbindLocalRegisterTable(loginClientInfo, ctx);
            AppKeyValidator.releaseReservedIfNeeded(loginClientInfo.getAppKey(), ctx);
            closeWithLoginFail(ctx, packet, "登录绑定失败", loginClientInfo.getIdentity());
            return;
        }
        if (!directoryOwned) {
            log.warn("登录 fencing 失败，目录已被更新会话覆盖 identity={}", loginClientInfo.getIdentity());
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.LOGIN_VERIFY_ERROR, "登录绑定失败：会话已被更新连接顶替", "AuthenticationHandler", packet);
            ClientHelper.unbindLocalRegisterTable(loginClientInfo, ctx);
            AppKeyValidator.releaseReservedIfNeeded(loginClientInfo.getAppKey(), ctx);
            closeWithLoginFail(ctx, packet, "登录绑定失败：会话已被更新连接顶替", loginClientInfo.getIdentity());
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
            log.warn("客户端: {} 登录成功，取消登录超时定时任务失败", loginClientInfo);
        }
        publishClientLoginOutsideEventLoop(ctx, loginClientInfo, loginClientInfo.getLastLoginTime());
    }

    /**
     * 登录事件必须可靠进入 Disruptor，但环满时 {@code publishEvent} 会等待可用槽位。
     * 当前方法只在 EventLoop 上执行一次有界线程池提交，避免登录洪峰或慢监听器反压整个网络线程。
     * 如果事件线程池已经过载，则关闭刚建立的连接，让客户端重新登录恢复完整的上线事件语义。
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
                        // 任务排队期间连接可能已经关闭；此时 closeFuture 会发布登出事件，禁止再补发上线事件。
                        if (ctx.channel().isActive()) {
                            MessageServerContext.publishEvent(loginEvent, true);
                        }
                    });
        } catch (RejectedExecutionException rejected) {
            // 成功回包已经写出，这里只关连接，不再补一条登录失败。
            log.error("登录事件提交被拒绝，关闭连接等待客户端重试 identity={}, channelId={}",
                    loginClientInfo.getIdentity(), ctx.channel().id().asShortText(), rejected);
            ctx.close();
        }
    }

    /**
     * 登录成功后安装管道：心跳读空闲（第一个 {@link IdleStateHandler} + {@link HeartBeatHandler}，可选）；
     * 业务读空闲为 {@link BusinessIdleStateHandler}（继承 {@link IdleStateHandler}，合并 PING 与读空闲事件处理，少一层 handler）。
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
     * @description 校验登录信息；{@code scope} 必须为 {@link LoginScopeEnum} 已定义取值；
     * identity 在客服 scope 下须在 {@code ouyunc_im_user} 存在且属于该 appKey；
     * 签名为 {@code MD5(appKey&identity&createTime_appSecret)}，createTime 允许
     * {@link MessageConstant#LOGIN_SIGNATURE_CREATE_TIME_SKEW_MS} 偏差。
     */
    public boolean validate(LoginContent loginContent) {
        return LoginAuthValidator.verify(loginContent);
    }
}
