package com.ouyunc.message.handler;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.exception.MessageException;
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
import com.ouyunc.base.utils.LoginSignatureUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ClientLoginEventPayload;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.domain.entity.AppEntity;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.protocol.NativePacketProtocol;
import com.ouyunc.message.validator.AppKeyValidator;
import com.ouyunc.message.validator.DeviceValidator;
import com.ouyunc.message.validator.LoginUserValidator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.ScheduledFuture;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;

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
            if (loginInfo != null) {
                log.warn("重复登录, 正在关闭连接!");
                ctx.close();
                return;
            }
            doLogin(ctx, packet);
            return;
        }
        // 非登录消息，已经登录放行
        if (loginInfo == null) {
            log.warn("请先登录!");
            ctx.close();
            return;
        }
        ctx.fireChannelRead(packet);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ScheduledFuture<?> timeoutFuture = ctx.executor().schedule(() -> {
            LoginContent loginInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
            if (loginInfo == null) {
                log.error("登录超时, 在规定时间：{} s 内未进行登录，现进行关闭连接: {}!", MessageServerContext.serverProperties().getServerLoginTimeout(), ctx.channel().id().asShortText());
                ctx.close();
            }
        }, MessageServerContext.serverProperties().getServerLoginTimeout(), TimeUnit.SECONDS);
        ctx.channel().attr(AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_LOGIN_TIMEOUT_SCHEDULED_FUTURE)).set(timeoutFuture);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 取消登录超时定时任务，避免资源泄漏
        boolean cancelled = cancelTimeoutFuture(ctx);
        if (cancelled) {
            log.debug("客户端: {} 连接关闭，已取消登录超时定时任务", ctx.channel().id().asShortText());
        }
        super.channelInactive(ctx);
    }

    /**
     * 取消登录超时定时任务
     * @param ctx
     * @return boolean
     */
    private boolean cancelTimeoutFuture(ChannelHandlerContext ctx) {
        boolean cancel = false;
        AttributeKey<ScheduledFuture<?>> attributeKey = AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_LOGIN_TIMEOUT_SCHEDULED_FUTURE);
        ScheduledFuture<?> timeoutFuture = ctx.channel().attr(attributeKey).get();
        if (timeoutFuture != null) {
            cancel = timeoutFuture.cancel(true);
            ctx.channel().attr(AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_LOGIN_TIMEOUT_SCHEDULED_FUTURE)).set(null);
        }
        return cancel;
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
            ctx.close();
            return;
        }
        // 摘流 / 拒绝新连接：滚动升级窗口内不再接受新登录
        if (!MessageServerContext.isAcceptingNewConnections()) {
            log.warn("客户端id: {} 登录被拒绝：服务摘流中", ctx.channel().id().asShortText());
            MessageServerContext.publishEvent(new MessageEvent(
                    ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_REFUSED_DRAIN, "服务摘流中，拒绝登录", packet),
                    MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return;
        }
        //将消息内容转成message
        LoginContent loginContent = JSON.parseObject(loginMessage.getContent(), LoginContent.class);
        loginContent.setScope(LoginScopeEnum.normalizeScope(loginContent.getScope()));
        // 设置设备类型
        byte deviceType = MessageServerContext.deviceType(loginContent.getAppKey(), packet.getDeviceType());
        // 做登录参数校验
        //1,进行参数合法校验，校验失败，结束 ；2,进行签名的校验，校验失败，结束，3，进行权限校验，校验失败，结束
        // 根据appKey 获取appSecret 然后拼接
        if (AppKeyValidator.INSTANCE.negate().verify(loginContent.getAppKey(), ctx) || DeviceValidator.INSTANCE.negate().verify(packet, ctx) || !validate(loginContent)) {
            log.warn("客户端id: {} 登录参数: {}，校验未通过！", ctx.channel().id().asShortText(), Serializer.JSON.serializeToString(loginContent));
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_VERIFY_ERROR, "登录校验未通过", packet), MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return;
        }
        String comboIdentity = IdentityUtil.generalComboIdentity(loginContent.getAppKey(), loginContent.getIdentity(), deviceType);
        LoginClientInfo cacheLoginClientInfo = MessageServerContext.remoteLoginClientInfoCache.get(CacheConstant.buildLoginCacheKey(loginContent.getAppKey(), comboIdentity));
        ChannelHandlerContext bindCtx = MessageServerContext.localLoginClientRegisterTable.get(comboIdentity);
        kickPreviousSessionIfPresent(ctx, packet, loginContent, loginMessage, loginTimestamp, cacheLoginClientInfo, bindCtx);
        // 获取使用的协议
        Protocol protocol = ctx.channel().attr(NativePacketProtocol.protocolAttrKey).get();
        if (protocol == null) {
            log.warn("Protocol not set on channel, closing connection: {}", ctx.channel().id().asShortText());
            ctx.close();
            return;
        }
        byte protocolValue = protocol.getProtocol();
        byte protocolVersion = protocol.getProtocolVersion();
        LoginClientInfo newLoginClientInfo = new LoginClientInfo(protocolValue, protocolVersion, MessageContext.messageProperties.getLocalServerAddress(), OnlineEnum.ONLINE, null, ClientHelper.calculateClientLoginExpireTime(loginContent.getHeartBeatExpireTime()), ClientHelper.calculateClientHeartBeatTimeout(loginContent.getHeartBeatExpireTime()), loginTimestamp, loginContent.getAppKey(), loginContent.getIdentity(), deviceType, loginContent.getSupportDeviceTypes(), loginContent.getSn(), loginContent.getSignature(), loginContent.getSignatureAlgorithm(), loginContent.getHeartBeatExpireTime(), loginTimestamp, loginContent.getEnableWill(), loginContent.getWillMessage(), loginContent.getEnableAlive(), loginContent.getAliveMessage(), loginContent.getScope(), loginContent.getBusinessIdleSeconds(), loginContent.getHeartBeatWaitRetry(), loginContent.getBusinessIdleCloseStrike());
        // 添加channel 关闭后释放资源的钩子, 该逻辑在DefaultSocketChannelInitializer 中进行调用
        Consumer<Channel> channelCloseHook = channel -> {
            //1,从channel中的attrMap取出相关属性
            final LoginClientInfo closingLocalloginClientInfo = ChannelAttrUtil.getChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
            if (closingLocalloginClientInfo != null) {
                // 这里不进行判空了，到这里肯定不为空（登录信息里面一定要有登录设备的类型）
                Byte clientLoginDeviceValue = closingLocalloginClientInfo.getDeviceType();
                String closingComboIdentity = IdentityUtil.generalComboIdentity(closingLocalloginClientInfo.getAppKey(), closingLocalloginClientInfo.getIdentity(), clientLoginDeviceValue);
                // 登录信息一致,才进行解绑，删除缓存信息
                MessageServerContext.localLoginClientRegisterTable.delete(closingComboIdentity);
                String loginClientInfoCacheKey = CacheConstant.buildLoginCacheKey(closingLocalloginClientInfo.getAppKey(), closingComboIdentity);
                // 获取分布式锁, 这里使用锁的目的，可以参考登录处理器的分布式锁，防止重复解绑 LoginMessageProcessor
                RLock lock = MessageServerContext.redissonClient.getLock(CacheConstant.buildIdentityBindOrUnbindLockCacheKey(closingLocalloginClientInfo.getAppKey(), closingComboIdentity));
                try {
                    if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                        LoginClientInfo closingRemoteLoginClientInfo = MessageServerContext.remoteLoginClientInfoCache.get(loginClientInfoCacheKey);
                        // 这里比较两个登录服务器地址是否一致的目的是因为，无论集群还是单服务 在ctx异步关闭时,有可能存在关闭的执行顺序比绑定客户端的方法执行的慢，导致缓存被覆盖，结果又给删除了缓存信息，导致数据错乱。
                        if (closingRemoteLoginClientInfo != null && closingLocalloginClientInfo.getLoginServerAddress().equals(closingRemoteLoginClientInfo.getLoginServerAddress()) && closingRemoteLoginClientInfo.getLastLoginTime() == closingLocalloginClientInfo.getLastLoginTime()) {
                            // 缓存中有没有登录信息都进行删除下
                            // 删除appKey 下的连接统计信息
                            RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();
                            redisTemplate.executePipelined(new SessionCallback<>() {
                                @SuppressWarnings("unchecked")
                                @Override
                                public <K, V> Object execute(@NotNull RedisOperations<K, V> operations) throws DataAccessException {
                                    // 删除登录信息
                                    operations.delete((K) loginClientInfoCacheKey);
                                    // 删除appKey 下的连接统计信息
                                    operations.opsForZSet().remove((K) (CacheConstant.buildConnectionsCacheKey(closingLocalloginClientInfo.getAppKey())), closingComboIdentity);
                                    return null;
                                }
                            });
                        } else {
                            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.UN_BIND_ERROR, "客户端解绑登录信息失败！缓存中不存在登录信息或登录地址不匹配", packet), MessageEventTypeEnum.EXCEPTION));
                        }
                    } else {
                        MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.UN_BIND_ERROR, "客户端解绑登录信息失败！获取分布式锁失败", packet), MessageEventTypeEnum.EXCEPTION));
                    }
                } catch (Exception e) {
                    MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.UN_BIND_ERROR, "客户端解绑登录信息失败！" + e.getMessage(), packet), MessageEventTypeEnum.EXCEPTION));
                    throw new MessageException(e);
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
                // 发送客户端离线事件， 可以处理发送遗嘱等客户端关闭后的操作逻辑
                MessageServerContext.publishEvent(new MessageEvent(closingLocalloginClientInfo, MessageEventTypeEnum.CLIENT_LOGOUT), true);
            }
        };
        // 设置channel 关闭后的回调
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_CHANNEL_CLOSE_HOOK, channelCloseHook);
        ClientHelper.bindAsync(ctx, newLoginClientInfo).whenComplete((unused, ex) ->
                ctx.executor().execute(() ->
                        completeLoginAfterRemoteBind(ctx, packet, loginContent, loginMessage,
                                deviceType, newLoginClientInfo, loginTimestamp, ex)));
    }

    /**
     * 重复登录：同 sn 仅静默断开旧连接；异 sn 发远程登录通知后断开。
     */
    private void kickPreviousSessionIfPresent(ChannelHandlerContext ctx, Packet packet, LoginContent loginContent,
                                              Message loginMessage, long loginTimestamp,
                                              LoginClientInfo cacheLoginClientInfo, ChannelHandlerContext bindCtx) {
        if (cacheLoginClientInfo == null && bindCtx == null) {
            return;
        }
        LoginClientInfo oldClientInfo = cacheLoginClientInfo;
        if (oldClientInfo == null && bindCtx != null) {
            oldClientInfo = ChannelAttrUtil.getChannelAttribute(bindCtx.channel(), MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        }
        if (oldClientInfo == null) {
            if (bindCtx != null && bindCtx.channel().isActive()) {
                bindCtx.close();
            }
            return;
        }
        boolean sameDevice = StringUtils.isNotBlank(oldClientInfo.getSn())
                && StringUtils.isNotBlank(loginContent.getSn())
                && oldClientInfo.getSn().equals(loginContent.getSn());
        if (sameDevice) {
            if (bindCtx != null && bindCtx.channel().isActive()) {
                bindCtx.close();
            }
            return;
        }
        Message kickMessage = new Message(
                MessageContext.idGenerator().generateIdStr(),
                null,
                loginContent.getIdentity(),
                MessageContentTypeEnum.REMOTE_LOGIN_CONTENT.getType(),
                Serializer.JSON.serializeToString(new ServerNotifyContent(
                        String.format(MessageConstant.REMOTE_LOGIN_NOTIFICATIONS, loginMessage.getMetadata().getClientIp()))),
                loginTimestamp,
                loginMessage.getMetadata());
        Packet kickPacket = new Packet(
                packet.getProtocol(),
                packet.getProtocolVersion(),
                MessageContext.idGenerator().generateId(),
                oldClientInfo.getDeviceType(),
                NetworkEnum.OTHER.getValue(),
                packet.getEncryptType(),
                packet.getSerializeAlgorithm(),
                MessageTypeEnum.SERVER_NOTIFY.getType(),
                kickMessage);
        Target kickTarget = Target.newBuilder()
                .appKey(oldClientInfo.getAppKey())
                .targetIdentity(oldClientInfo.getIdentity())
                .targetServerAddress(oldClientInfo.getLoginServerAddress())
                .deviceType(oldClientInfo.getDeviceType())
                .protocol(oldClientInfo.getProtocol())
                .protocolVersion(oldClientInfo.getProtocolVersion())
                .build();
        MessageHelper.syncSendMessageWithoutInterceptor(kickPacket, kickTarget);
        if (bindCtx != null && bindCtx.channel().isActive()) {
            bindCtx.close();
        }
    }

    /**
     * Redis 与本地注册表绑定成功后，在 EventLoop 上完成登录 ACK 与管道安装。
     */
    private void completeLoginAfterRemoteBind(ChannelHandlerContext ctx, Packet packet, LoginContent loginContent,
                                              Message loginMessage, byte deviceType, LoginClientInfo loginClientInfo,
                                              long loginTimestamp, Throwable bindError) {
        if (!ctx.channel().isActive()) {
            ClientHelper.unbindLocalRegisterTable(loginClientInfo);
            return;
        }
        if (bindError != null) {
            log.error("客户端: {} 登录绑定失败", loginClientInfo, bindError);
            MessageServerContext.publishEvent(new MessageEvent(
                    ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_VERIFY_ERROR,
                            "登录绑定失败: " + bindError.getMessage(), packet),
                    MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return;
        }
        installLoginIdlePipeline(ctx, loginContent);
        Message loginAckMessage = new Message(
                MessageContext.idGenerator().generateIdStr(),
                null,
                loginClientInfo.getIdentity(),
                MessageContentTypeEnum.LOGIN_RESPONSE_SUCCESS_CONTENT.getType(),
                MessageContentTypeEnum.LOGIN_RESPONSE_SUCCESS_CONTENT.getDescription(),
                loginTimestamp,
                loginMessage.getMetadata());
        Packet loginAckPacket = new Packet(
                packet.getProtocol(),
                packet.getProtocolVersion(),
                MessageContext.idGenerator().generateId(),
                deviceType,
                packet.getNetworkType(),
                packet.getEncryptType(),
                packet.getSerializeAlgorithm(),
                MessageTypeEnum.LOGIN.getType(),
                loginAckMessage);
        MessageHelper.syncSendMessage(loginAckPacket, Target.newBuilder()
                .appKey(loginClientInfo.getAppKey())
                .targetIdentity(loginClientInfo.getIdentity())
                .targetServerAddress(loginClientInfo.getLoginServerAddress())
                .deviceType(deviceType)
                .protocol(packet.getProtocol())
                .protocolVersion(packet.getProtocolVersion())
                .build());
        if (!cancelTimeoutFuture(ctx)) {
            log.warn("客户端: {} 登录成功，取消登录超时定时任务失败", loginClientInfo);
        }
        MessageServerContext.publishEvent(
                new MessageEvent(new ClientLoginEventPayload(loginClientInfo, ctx), MessageEventTypeEnum.CLIENT_LOGIN, loginTimestamp),
                true);
        ctx.pipeline().remove(this);
    }

    /**
     * 登录成功后安装管道：心跳读空闲（第一个 {@link IdleStateHandler} + {@link HeartBeatHandler}，可选）；
     * 业务读空闲为 {@link BusinessIdleStateHandler}（继承 {@link IdleStateHandler}，合并 PING 与读空闲事件处理，少一层 handler）。
     */
    private void installLoginIdlePipeline(ChannelHandlerContext ctx, LoginContent loginContent) {
        String pipelineAnchor = MessageConstant.CONVERT_2_PACKET_HANDLER;
        Integer heartbeatExpireTime = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_HEARTBEAT_TIMEOUT);
        boolean heartbeatInstalled = MessageServerContext.serverProperties().isClientHeartBeatEnable() && heartbeatExpireTime != null;
        if (heartbeatInstalled) {
            if (loginContent.getHeartBeatWaitRetry() > 0) {
                ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_HEARTBEAT_WAIT_RETRY, loginContent.getHeartBeatWaitRetry());
            }
            ctx.pipeline()
                    .addAfter(pipelineAnchor, MessageConstant.CLIENT_LOGIN_KEEP_ALIVE_HANDLER, new LoginKeepAliveHandler())
                    .addAfter(MessageConstant.CLIENT_LOGIN_KEEP_ALIVE_HANDLER, MessageConstant.HEART_BEAT_IDLE_HANDLER, new IdleStateHandler(heartbeatExpireTime, NumberConstant.NUMBER_0, NumberConstant.NUMBER_0, TimeUnit.SECONDS))
                    .addAfter(MessageConstant.HEART_BEAT_IDLE_HANDLER, MessageConstant.HEART_BEAT_HANDLER, new HeartBeatHandler());
            pipelineAnchor = MessageConstant.HEART_BEAT_HANDLER;
        }
        if (!LoginScopeEnum.isCustomerService(loginContent.getScope()) || loginContent.getBusinessIdleSeconds() <= 0) {
            return;
        }
        int bizSec = loginContent.getBusinessIdleSeconds();
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
        if (loginContent == null || !LoginScopeEnum.isDefinedType(loginContent.getScope())) {
            return false;
        }
        if (StringUtils.isAnyBlank(loginContent.getAppKey(), loginContent.getIdentity(), loginContent.getSignature())) {
            return false;
        }
        // 客服坐席/访客：identity 必须已是本应用 ouyunc_im_user（签名不能代替建档）
        if (LoginScopeEnum.isCustomerService(loginContent.getScope())
                && !LoginUserValidator.userExists(loginContent.getAppKey(), loginContent.getIdentity())) {
            return false;
        }
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();
        AppEntity app = redisTemplate.<String, AppEntity>opsForHash()
                .get(CacheConstant.buildAppKeysCacheKey(), loginContent.getAppKey());
        if (app == null || StringUtils.isBlank(app.getAppSecret())) {
            log.warn("登录签名校验失败：appKey={} 不存在或无 appSecret", loginContent.getAppKey());
            return false;
        }
        String secret = app.getAppSecret();
        long createTime = loginContent.getCreateTime();
        if (!LoginSignatureUtil.isCreateTimeValid(createTime, TimeUtil.currentTimeMillis())) {
            log.warn("登录签名校验失败：createTime 无效或过期 appKey={} identity={}",
                    loginContent.getAppKey(), loginContent.getIdentity());
            return false;
        }
        String raw = LoginSignatureUtil.buildRaw(
                loginContent.getAppKey(), loginContent.getIdentity(), createTime, secret);
        Encrypt.AsymmetricEncrypt algo = Encrypt.AsymmetricEncrypt.prototype(loginContent.getSignatureAlgorithm());
        if (algo == null) {
            algo = Encrypt.AsymmetricEncrypt.MD5;
        }
        return algo.validate(raw, loginContent.getSignature());
    }
}
