package com.ouyunc.message.processor;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.base.model.RequestSession;
import com.ouyunc.base.constant.enums.FriendJoinPolicy;
import com.ouyunc.base.constant.enums.RequestSessionProgress;
import com.ouyunc.domain.entity.UserEntity;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.DistributedLockHelper;
import com.ouyunc.message.helper.RequestNotifyHelper;
import com.ouyunc.message.validator.AuthValidator;
import com.ouyunc.message.validator.BlackListValidator;
import com.ouyunc.message.validator.FromToValidator;
import com.ouyunc.message.validator.PermissionValidator;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 加好友：自动同意通知申请人；待审通知被申请人（发起方不推送）。
 */
public final class One2OneJoinFriendRequestMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(One2OneJoinFriendRequestMessageBiProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.ONE_2_ONE_FRIEND_REQUEST_JOIN;
    }

    @SuppressWarnings("unchecked")
    @Override
    public DefaultRepository repository() {
        return super.repository();
    }

    @Override
    public Mono<Boolean> preProcess(ChannelHandlerContext ctx, Packet packet) {
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            log.error("校验消息失败: {} 认证未通过,开始关闭channel", packet);
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过!", packet), MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return Mono.just(false);
        }
        // 权限等校验通过后由 continueWhenPassed 归档；此处仅做 QOS_DUP 展开
        if (MessageContext.isQosEnable() && qosPreHandle(ctx, packet)) {
            return Mono.just(false);
        }
        return continueWhenPassedOrAck(ctx, packet,
                PermissionValidator.INSTANCE.negate()
                        .or(FromToValidator.INSTANCE)
                        .or(BlackListValidator.INSTANCE)
                        .verify(packet, ctx),
                "权限不足/在黑名单中/被屏蔽/发送方和接收方相等, 请知悉。该消息 {} 被忽略");
    }

    /**
     * 处理加好友请求
     * @param ctx
     * @param packet
     */
    @Override
    public Mono<Void> process(ChannelHandlerContext ctx, Packet packet) {
        return Mono.fromRunnable(() -> {
            // 1. 保存消息
            Message message = packet.getMessage();
            String appKey = message.getMetadata().getAppKey();
            String sessionId = IdentityUtil.sessionId(message.getFrom(), message.getTo());

            // 分布式锁保护的业务逻辑统一调度到业务线程池，避免阻塞 Netty EventLoop
            String lockKey = CacheConstant.buildFriendRequestLockCacheKey(appKey, sessionId);
            DistributedLockHelper.runWithLock(packet, lockKey, ExceptionCodeEnum.BIND_FRIEND_ERROR, () -> {
                // 获取请求会话
                RequestSession requestSession = repository().getFriendRequestSession(appKey, message.getFrom(), message.getTo());
                // 已经是好友：幂等成功，回 ACK，避免 QoS 客户端空转
                if (repository().isFriend(appKey, message.getFrom(), message.getTo())) {
                    log.warn("已经是好友, 幂等 ACK; {}", packet);
                    RequestNotifyHelper.dispatch(ctx, packet, appKey, RequestNotifyHelper.userOnly(message.getFrom()));
                    return;
                }
                // AGREEING/REFUSING 残留但已非好友：清会话后允许再申请
                if (null != requestSession && requestSession.getProgress() > RequestSessionProgress.JOINING.value()) {
                    log.warn("{} 和 {} 好友请求会话残留 progress={}，清除后允许重新申请",
                            message.getFrom(), message.getTo(), requestSession.getProgress());
                    repository().deleteFriendRequestSession(appKey, message.getFrom(), message.getTo());
                    requestSession = null;
                }
                // 获取当前对方的配置信息
                UserEntity toUserEntity = repository().getUserEntity(appKey, message.getTo());
                if (toUserEntity == null) {
                    log.error("对方:{} 不存在，请检查数据！", message.getTo());
                    MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.USER_NOT_EXIST, message.getTo() + "用户不存在！", packet), MessageEventTypeEnum.EXCEPTION));
                    ackRequestSettled(ctx, packet);
                    return;
                }
                // 尝试设置请求会话信息
                RequestSession session = requestSession != null ? requestSession
                        : RequestSession.newBuilder().sessionId(MessageContext.idGenerator().generateIdStr()).build();

                // 判断对方是否是自动同意加好友
                if (FriendJoinPolicy.AUTO_PASS.value().equals(toUserEntity.getFriendJoinPolicy())) {
                    session.setProgress(RequestSessionProgress.AGREEING.value());
                    if (!repository().autoPassBindFriend(packet, session, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)) {
                        log.error("自动处理绑定好友失败: {}", packet);
                        MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存一对一自动绑定好友请求消息异常!", packet), MessageEventTypeEnum.EXCEPTION), true);
                        return;
                    }
                    RequestNotifyHelper.dispatch(ctx, packet, appKey, RequestNotifyHelper.userOnly(message.getFrom()));
                } else {
                    session.setProgress(RequestSessionProgress.JOINING.value());
                    if (!repository().saveJoinFriendRequestMessage(packet, session, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)) {
                        log.error("Failed to save one-to-one join friend request message: {}", packet);
                        MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存一对一加好友请求消息异常!", packet), MessageEventTypeEnum.EXCEPTION), true);
                        return;
                    }
                    RequestNotifyHelper.dispatch(ctx, packet, appKey, RequestNotifyHelper.userOnly(message.getTo()));
                }
                repository().publishPacketAsync(MqConstant.MQ_FRIEND_REQUEST_TOPIC, sessionId, packet,
                        "处理一对一添加好友请求 MQ 旁路");
            });
            });
    }
}
