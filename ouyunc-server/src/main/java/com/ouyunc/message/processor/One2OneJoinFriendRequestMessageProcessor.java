package com.ouyunc.message.processor;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.SnowflakeUtil;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.domain.base.RequestSession;
import com.ouyunc.domain.constants.YesOrNo;
import com.ouyunc.domain.entity.UserEntity;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.validator.AuthValidator;
import com.ouyunc.message.validator.BlackListValidator;
import com.ouyunc.message.validator.FriendValidator;
import com.ouyunc.message.validator.PermissionValidator;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 加好友
 */
public final class One2OneJoinFriendRequestMessageProcessor extends AbstractMessageProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(One2OneJoinFriendRequestMessageProcessor.class);

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
        repository().save(packet).whenComplete((sendResult, ex)->{
            if (ex == null) {
                // 两个都校验通过才放行
                if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
                    // 关闭当前 channel，这里会触发 DefaultSocketChannelInitializer 中的关闭逻辑
                    log.error("校验消息失败: {} 认证未通过,开始关闭channel", packet);
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过!", packet), true);
                    ctx.close();
                    return;
                }
                // 校验是否拥有相关权限 permission （是有有单聊，甚至某种内容类型的权限，如不能发语音，视频消息，只能发文本，都可以在这里做校验拦截）
                // 屏蔽和拉黑的效果目前是一样的功能，都不能将将消息发到对方
                // 校验是否被拉黑,如果被拉黑 （无论是否是好友，都可以拉黑）判断当前会话是否被拒绝和同意中，防止mq 延迟消费
                if (PermissionValidator.INSTANCE.negate().or(BlackListValidator.INSTANCE).verify(packet, ctx)) {
                    log.warn("验证不通过。没有权限/被拉黑，请知悉。该消息 {} 被忽略", packet);
                    return;
                }
                ctx.fireChannelRead(packet);
            } else {
                // 发送失败
                log.error("Failed to send message: {} " , ex.getMessage());
                MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, "通过发送mq保存消息异常!", packet), true);
            }
        });
    }

    /**
     * 处理加好友请求
     * @param ctx
     * @param packet
     */
    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.info("One2OneJoinFriendRequestMessageContentProcessor 正在处理加好友请求 {} ...", packet);
        // 1. 保存消息
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        String sessionId = IdentityUtil.sessionId(message.getFrom(), message.getTo());

        // 加锁
        RLock lock = MessageServerContext.redissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.FRIEND_REQUEST + sessionId);
        try {
            if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                // 如果是好友或者处理中，直接返回
                if (FriendValidator.INSTANCE.verify(packet, ctx)) {
                    log.warn("已经是好友, 请知悉; {}" ,packet);
                    return;
                }
                // 获取请求会话
                RequestSession requestSession = repository().getRequestSession(appKey, message.getFrom(), message.getTo());
                if (null != requestSession && requestSession.getProgress() > MessageConstant.FRIEND_REQUEST_PROGRESS_JOINING) {
                    log.warn("{} 和 {} 会话存在正在处理中的好友请求，拒绝和同意还未结束处理", message.getFrom(), message.getTo());
                    return;
                }
                // 获取当前对方的配置信息,是否是自动同意加好友，
                UserEntity toUserEntity = repository().getUserEntity(appKey, message.getTo());
                if (toUserEntity == null) {
                    log.error("对方:{} 不存在，请检查数据！", message.getTo());
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.USER_NOT_EXIST, message.getTo() + "用户不存在！", packet));
                    return;
                }
                // 判断对方是否是自动同意加好友
                if (YesOrNo.YES.getCode().equals(toUserEntity.getFriendJoinPolicy())) {
                    // 是自动添加好友
                    if (!repository().autoPassBindFriend(appKey, packet, requestSession == null ? String.valueOf(SnowflakeUtil.nextId()) : requestSession.getSessionId(), MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)) {
                        log.error("自动处理绑定好友失败: {}", packet);
                        MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存一对一自动绑定好友请求消息异常!", packet), true);
                        return;
                    }
                }else if (!repository().saveJoinFriendRequestMessage(packet, sessionId, requestSession == null ? String.valueOf(SnowflakeUtil.nextId()) : requestSession.getSessionId(), MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)) {
                    log.error("Failed to save one-to-one join friend request message: {}", packet);
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存一对一加好友请求消息异常!", packet), true);
                    return;
                }
                // 这里不保存到session 缓存中,保存到临时的会话消息中，该好友请求的消息可以对其进行定期清理；这里考虑个问题，到底是先发mq还是先等方法处理完再发mq
                repository().savePacket2Mq(MqConstant.KAFKA_FRIEND_REQUEST_TOPIC, sessionId, packet).whenComplete((result, ex) ->{
                    if (ex != null) {
                        log.error("请求添加好友，发送mq异常，原因：{}", ex.getMessage());
                        MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, "处理一对一添加好友请求异常！" + ex.getMessage(), packet), true);
                    }else {
                        // 如果接收方在线，则直接发送消息
                        List<LoginClientInfo> toLoginClientInfos = ClientHelper.onlineAll(message.getMetadata().getAppKey(), message.getTo());
                        if (CollectionUtils.isNotEmpty(toLoginClientInfos)) {
                            MessageHelper.asyncSendMessage(packet, toLoginClientInfos);
                        }
                        // 处理成功则转到下个处理器
                        ctx.fireChannelRead(packet);
                    }
                });
            } else {
                log.error("Failed to lock one-to-one join friend request message: {}", packet);
                MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.ACQUIRE_LOCK_ERROR, "获取加好友请求锁失败", packet), true);
            }
        } catch (Exception e) {
            log.error("Failed to handle one-to-one join friend request message: {}", packet);
            MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.BIND_FRIEND_ERROR, "处理一对一加好友请求异常！" + e.getMessage(), packet), true);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

    }
}
