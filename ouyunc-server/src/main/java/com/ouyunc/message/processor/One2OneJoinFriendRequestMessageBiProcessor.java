package com.ouyunc.message.processor;

import com.ouyunc.base.executor.ThreadPoolManager;
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
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 异步存储packet（目前只是保存相关信息，不做扩展，以后可以做数据分析使用），这里将该数据存储到时序数据库中
        ThreadPoolManager.messageProcessorExecutor().execute(() -> repository().publishArchiveAsync(packet));
        // 两个都校验通过才放行
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            // 关闭当前 channel，这里会触发 DefaultSocketChannelInitializer 中的关闭逻辑
            log.error("校验消息失败: {} 认证未通过,开始关闭channel", packet);
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过!", packet), MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return;
        }
        // 校验是否拥有相关权限 permission （是有有单聊，甚至某种内容类型的权限，如不能发语音，视频消息，只能发文本，都可以在这里做校验拦截）
        // 屏蔽和拉黑的效果目前是一样的功能，都不能将将消息发到对方
        // 校验是否被拉黑,如果被拉黑 （无论是否是好友，都可以拉黑）判断当前会话是否被拒绝和同意中，防止mq 延迟消费
        PermissionValidator.INSTANCE.negate()
                    .or(FromToValidator.INSTANCE)
                    .or(BlackListValidator.INSTANCE)
                    .verify(packet, ctx)
                    .onErrorResume(error -> {
                        log.error("校验过程中出现异常: {}", error.getMessage());
                        return Mono.just(true); // 出现异常时默认校验不通过
                    }).flatMap(result -> {
                        if (result) {
                            log.warn("权限不足/在黑名单中/被屏蔽/发送方和接收方相等, 请知悉。该消息 {} 被忽略", packet);
                            return Mono.empty(); // 校验不通过，不传递消息
                        }
                        return Mono.just(packet); // 校验通过，继续传递消息
                    }).subscribe(ctx::fireChannelRead);
    }

    /**
     * 处理加好友请求
     * @param ctx
     * @param packet
     */
    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        // 1. 保存消息
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        String sessionId = IdentityUtil.sessionId(message.getFrom(), message.getTo());

        // 分布式锁保护的业务逻辑统一调度到业务线程池，避免阻塞 Netty EventLoop
        String lockKey = CacheConstant.buildFriendRequestLockCacheKey(appKey, sessionId);
        DistributedLockHelper.runWithLock(packet, lockKey, ExceptionCodeEnum.BIND_FRIEND_ERROR, () -> {
            // 获取请求会话
            RequestSession requestSession = repository().getFriendRequestSession(appKey, message.getFrom(), message.getTo());
            if (null != requestSession && requestSession.getProgress() > RequestSessionProgress.JOINING.value()) {
                log.warn("{} 和 {} 会话存在正在处理中的好友请求，拒绝和同意还未结束处理", message.getFrom(), message.getTo());
                return;
            }
            // 如果已经是好友，直接返回
            if (repository().isFriend(appKey, message.getFrom(), message.getTo())) {
                log.warn("已经是好友, 请知悉; {}", packet);
                return;
            }
            // 获取当前对方的配置信息
            UserEntity toUserEntity = repository().getUserEntity(appKey, message.getTo());
            if (toUserEntity == null) {
                log.error("对方:{} 不存在，请检查数据！", message.getTo());
                MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.USER_NOT_EXIST, message.getTo() + "用户不存在！", packet), MessageEventTypeEnum.EXCEPTION));
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
    }
}
