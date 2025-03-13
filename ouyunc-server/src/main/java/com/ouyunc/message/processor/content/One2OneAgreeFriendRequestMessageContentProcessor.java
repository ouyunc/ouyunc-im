package com.ouyunc.message.processor.content;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageContentType;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.processor.AbstractBaseProcessor;
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
public class One2OneAgreeFriendRequestMessageContentProcessor extends AbstractBaseProcessor<Integer> {
    private static final Logger log = LoggerFactory.getLogger(One2OneAgreeFriendRequestMessageContentProcessor.class);

    @Override
    public MessageContentType type() {
        return MessageContentTypeEnum.FRIEND_REQUEST_REFUSE_CONTENT;
    }

    @SuppressWarnings("unchecked")
    @Override
    public DefaultRepository repository() {
        return super.repository();
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
        RLock lock = MessageServerContext.redissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.FRIEND_REQUEST_AGREE + sessionId);
        try {
            if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                // 判断现在是否是好友如果是好友则直接返回
                if (repository().isFriend(appKey, from, to)) {
                    log.warn("{} 和 {} 已经是好友关系，忽略该消息: {}", from, to, packet);
                    return;
                }
                // 保存消息
                if (!repository().saveMessage(packet, sessionId, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)) {
                    log.error("Failed to save one-to-one agree friend request message: {}", packet);
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存一对一同意好友请求消息异常!", packet), true);
                    return;
                }
                // 保存消息后，则绑定好友关系,先发送绑定好友的mq消息，发送成功后
                repository().savePacket2Mq(MqConstant.KAFKA_BIND_FRIEND_TOPIC, packet).whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("绑定好友关系，发送mq，原因：{}", ex.getMessage());
                        MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, "处理一对一同意好友请求绑定异常！" + ex.getMessage(), packet), true);
                    } else {
                        // 开始保存好友关系到redis中
                        if (!repository().bindFriend(appKey, packet)) {
                            log.error("绑定好友关系异常: {}", packet);
                            MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.BIND_FRIEND_ERROR, "处理一对一同意好友请求绑定异常！", packet), true);
                            return;
                        }
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
