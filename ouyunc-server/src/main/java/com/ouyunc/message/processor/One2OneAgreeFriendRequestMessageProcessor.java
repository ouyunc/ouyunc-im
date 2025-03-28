package com.ouyunc.message.processor;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.domain.base.RequestSession;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.validator.*;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 同意加好友请求
 */
public final class One2OneAgreeFriendRequestMessageProcessor extends AbstractMessageProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(One2OneAgreeFriendRequestMessageProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.ONE_2_ONE_FRIEND_REQUEST_AGREE;
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
                // 校验是否被拉黑,如果被拉黑 （无论是否是好友，都可以拉黑） 判断是否有记录
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
     * 处理同意好友请求，在发送该消息前，可以判断双方是否已经是好友，如果是好友，则不发送该消息即可，如果选择发送该消息，会给对方推送一条同意的消息，注意逻辑处理；
     * @param ctx
     * @param packet
     */
    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.info("One2OneAgreeFriendRequestMessageContentProcessor 正在处理同意好友请求 {} ...", packet);
        // 1. 保存消息
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        Metadata metadata = message.getMetadata();
        String appKey = metadata.getAppKey();
        String sessionId = IdentityUtil.sessionId(from, to);
        // 处理同意添加逻辑
        // 加锁
        RLock lock = MessageServerContext.redissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.FRIEND_REQUEST + sessionId);
        try {
            if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                // 如果是好友或者处理中或没有好友请求记录，直接返回
                if (FriendValidator.INSTANCE.verify(packet, ctx)) {
                    log.warn("已经是好友, 请知悉; {}" ,packet);
                    return;
                }
                // 获取请求会话
                RequestSession requestSession = repository().getRequestSession(appKey, message.getTo(), message.getFrom());
                if (null == requestSession || requestSession.getProgress() != MessageConstant.FRIEND_REQUEST_PROGRESS_JOINING) {
                    log.warn("不存在加好友请求记录或存在正在处理的好友请求，该消息忽略");
                    return;
                }
                // 保存消息
                // 保存消息&开始保存好友关系到redis中
                if (!repository().agreeBindFriend(appKey, packet, requestSession.getSessionId(), MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)) {
                    log.error("绑定好友关系异常: {}", packet);
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.BIND_FRIEND_ERROR, "处理一对一同意好友请求绑定异常！", packet), true);
                    return;
                }
                // 保存消息后，则绑定好友关系,先发送绑定好友的mq消息，发送成功后
                repository().savePacket2Mq(MqConstant.KAFKA_FRIEND_REQUEST_TOPIC, sessionId, packet).whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("绑定好友关系，发送mq，原因：{}", ex.getMessage());
                        MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, "处理一对一同意好友请求绑定异常！" + ex.getMessage(), packet), true);
                    } else {
                        //  如果绑定成功如果接收方在线，则直接发送消息
                        List<LoginClientInfo> toLoginClientInfos = ClientHelper.onlineAll(appKey, to);
                        if (CollectionUtils.isNotEmpty(toLoginClientInfos)) {
                            MessageHelper.asyncSendMessage(packet, toLoginClientInfos);
                        }
                        // 处理成功则转到下个处理器
                        ctx.fireChannelRead(packet);
                    }
                });
            } else {
                log.error("Failed to lock one-to-one agree friend request message: {}", packet);
                MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.ACQUIRE_LOCK_ERROR, "获取同意加好友锁失败", packet), true);
            }
        } catch (Exception e) {
            log.error("Failed to handle one-to-one agree friend request message: {}", packet);
            MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.BIND_FRIEND_ERROR, "处理一对一同意好友请求绑定异常！" + e.getMessage(), packet), true);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

    }
}
