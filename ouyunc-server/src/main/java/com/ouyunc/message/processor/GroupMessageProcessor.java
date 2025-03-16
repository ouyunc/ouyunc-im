package com.ouyunc.message.processor;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.validator.AuthValidator;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.ouyunc.message.context.MessageServerContext.redissonClient;


/**
 * 群聊消息处理器
 */
public class GroupMessageProcessor extends AbstractMessageProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(GroupMessageProcessor.class);


    @Override
    public MessageType type() {
        return MessageTypeEnum.GROUP;
    }

    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 异步存储packet（目前只是保存相关信息，不做扩展，以后可以做数据分析使用），这里将该数据存储到时序数据库中
        repository().save(packet).whenComplete((sendResult, ex) -> {
            if (ex == null) {
                if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
                    // 关闭当前 channel，这里会触发 DefaultSocketChannelInitializer 中的关闭逻辑
                    log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过", packet), true);
                    ctx.close();
                    return;
                }
                // 校验是否拥有相关权限 permission （对方是否被拉黑，禁用等）

                ctx.fireChannelRead(packet);
            } else {
                // 发送失败
                log.error("Failed to send message: {} ", ex.getMessage());
                MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, "通过发送mq保存消息异常!", packet), true);
            }
        });

    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.debug("Processing group message...");
        // 1. 尝试使用内容处理器
        if (processWithContentProcessor(ctx, packet)) {
            return;
        }
        // 2. 校验群中是否存在群成员
        // 获取群组成员登录标识id，如果群里面没有人是不允许往里面发消息的
        Set<String> groupUserIdentitySet = repository().groupUsersIdentity(packet);
        if (CollectionUtils.isEmpty(groupUserIdentitySet)) {
            log.error("群组：{}, 不存在群成员！群消息： {}", packet.getMessage().getTo(), packet);
            MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "群组不存在群成员", packet), true);
            return;
        }
        // 将groupUserIdentitySet排除掉发送方
        groupUserIdentitySet.remove(packet.getMessage().getFrom());
        // 2. 保存消息
        if (!saveGroupMessage(packet, groupUserIdentitySet)) {
            return;
        }
        // 3. 处理特殊消息类型
        if (!processSpecialMessageContentType(ctx, packet, groupUserIdentitySet)) {
            // 4. 发送消息给接收方
            deliverMessage(packet, groupUserIdentitySet);
            // 处理成功则转到下个处理器
            ctx.fireChannelRead(packet);
        }
    }


    /**
     * 使用内容处理器处理消息
     */
    private boolean processWithContentProcessor(ChannelHandlerContext ctx, Packet packet) {
        AbstractBaseProcessor<? extends Number> processor = MessageServerContext.messageContentProcessorCache.get(packet.getMessage().getContentType());
        if (processor != null) {
            processor.process(ctx, packet);
            return true;
        }
        return false;
    }


    /**
     * 保存群组消息
     */
    private boolean saveGroupMessage(Packet packet, Set<String> groupMembers) {
        Message message = packet.getMessage();
        if (MessageContext.messageProperties.isQosEnable() && message.getQos() > QosLevelEnum.QOS_0.getLevel() ? saveQosMessage(packet, groupMembers) : saveNonQosMessage(packet, message.getTo())) {
            return true;
        }
        log.error("Failed to save group message: {}", packet);
        MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存群组消息异常!", packet), true);
        return false;
    }

    /**
     * 保存Qos消息
     *
     * @param packet
     * @param groupMembers
     * @return
     */
    private boolean saveQosMessage(Packet packet, Set<String> groupMembers) {
        return repository().batchSaveMessage(
                packet,
                groupMembers,
                MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP
        );
    }

    /**
     * 保存非Qos消息
     *
     * @param packet
     * @param sessionId
     * @return
     */
    private boolean saveNonQosMessage(Packet packet, String sessionId) {
        return repository().saveMessage(
                packet,
                sessionId,
                MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP
        );
    }


    /**
     * 处理特殊消息类型
     */
    private boolean processSpecialMessageContentType(ChannelHandlerContext ctx, Packet packet, Set<String> groupUserIdentitySet) {
        Message message = packet.getMessage();
        if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == message.getContentType()) {
            repository().savePacket2Mq(MqConstant.KAFKA_WITHDRAW_MESSAGE_TOPIC, packet).whenComplete((result, ex)->{
                if (ex != null) {
                    log.error("群撤回消息，发送mq异常，原因：{}", ex.getMessage());
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, "群撤销消息发送mq异常!", packet), true);
                }else {
                    if (processWithdrawMessage(packet, groupUserIdentitySet)) {
                        // 4. 发送消息给接收方
                        deliverMessage(packet, groupUserIdentitySet);
                        // 处理成功则转到下个处理器
                        ctx.fireChannelRead(packet);
                    }
                }
            });
            return true;
        } else if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == message.getContentType()) {
            repository().savePacket2Mq(MqConstant.KAFKA_READ_RECEIPT_MESSAGE_TOPIC, packet).whenComplete((result, ex)->{
                if (ex != null) {
                    log.error("群已读消息，发送mq异常，原因：{}", ex.getMessage());
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, "群已读消息发送mq异常!", packet), true);
                }else {
                    if (processReadReceiptMessage(packet, groupUserIdentitySet)) {
                        // 4. 发送消息给接收方
                        deliverMessage(packet, groupUserIdentitySet);
                        // 处理成功则转到下个处理器
                        ctx.fireChannelRead(packet);
                    }
                }
            });
            return true;
        }
        return false;
    }


    /**
     * 处理撤回消息
     */
    private boolean processWithdrawMessage(Packet packet, Set<String> groupUserIdentitySet) {
        return processWithLock(packet,
                () -> repository().withdrawMessage(packet, getSessionId(packet), groupUserIdentitySet),
                ExceptionCodeEnum.WITHDRAW_MESSAGE_ERROR,
                "群撤销消息异常"
        );
    }

    /**
     * 处理已读回执消息
     */
    private boolean processReadReceiptMessage(Packet packet, Set<String> groupUserIdentitySet) {
        return processWithLock(packet,
                () -> repository().readReceiptMessage(packet, packet.getMessage().getTo(), MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP),
                ExceptionCodeEnum.READ_RECEIPT_MESSAGE_ERROR,
                "群读已回执消息异常"
        );
    }


    /**
     * 使用分布式锁处理消息
     */
    private boolean processWithLock(Packet packet,
                                    Supplier<Boolean> processor,
                                    ExceptionCodeEnum errorCode,
                                    String errorMessage) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String sessionId = getSessionId(packet);

        RLock multiLock = createMultiLock(metadata.getAppKey(), sessionId, message.getTo());

        try {
            if (!tryLock(multiLock)) {
                publishLockError(packet, errorCode, errorMessage);
                return false;
            }

            if (!processor.get()) {
                log.error("Failed to process message with lock: {}", packet);
                MessageServerContext.publishEvent(new ExceptionEvent(errorCode, errorMessage, packet), true);
                return false;
            }
            return true;

        } catch (Exception e) {
            log.error("Error while processing message with lock", e);
            MessageServerContext.publishEvent(new ExceptionEvent(errorCode, errorMessage, packet), true);
            return false;

        } finally {
            releaseLock(multiLock);
        }
    }


    /**
     * 获取sessionId
     *
     * @param packet
     * @return
     */
    private String getSessionId(Packet packet) {
        return packet.getMessage().getTo();
    }

    private RLock createMultiLock(String appKey, String sessionId, String to) {
        return redissonClient.getMultiLock(
                redissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.SESSION + sessionId),
                redissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.OFFLINE + to)
        );
    }

    private boolean tryLock(RLock lock) throws InterruptedException {
        return lock.tryLock(
                MessageConstant.LOCK_WAIT_TIME,
                MessageConstant.LOCK_LEASE_TIME,
                TimeUnit.SECONDS
        );
    }

    private void releaseLock(RLock lock) {
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    private void publishLockError(Packet packet, ExceptionCodeEnum code, String message) {
        log.error("Failed to acquire lock: {}", packet);
        MessageServerContext.publishEvent(new ExceptionEvent(code, message, packet), true);
    }


    /**
     * 发送消息给接收方
     */
    private void deliverMessage(Packet packet, Set<String> groupMembers) {
        Message message = packet.getMessage();
        if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == message.getContentType() || MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == message.getContentType()) {
            deliver2AllGroupMembers(packet, groupMembers);
        } else {
            List<String> atList = packet.getMessage().getAt();
            if (CollectionUtils.isNotEmpty(atList)) {
                deliverAtMessage(packet, atList, groupMembers);
            }
        }
    }


    protected void deliver2AllGroupMembers(Packet packet, Set<String> groupMembers) {
        groupMembers.forEach(member -> deliverToMember(packet, member));
    }

    protected void deliverAtMessage(Packet packet, List<String> atList, Set<String> groupMembers) {
        atList.stream()
                .filter(groupMembers::contains)
                .forEach(member -> deliverToMember(packet, member));
    }

    private void deliverToMember(Packet packet, String memberIdentity) {
        List<LoginClientInfo> clientInfos = ClientHelper.onlineAll(
                packet.getMessage().getMetadata().getAppKey(),
                memberIdentity
        );
        if (CollectionUtils.isEmpty(clientInfos)) {
            log.warn("Member {} is offline, message stored", memberIdentity);
            return;
        }
        MessageHelper.asyncSendMessage(packet, clientInfos);
    }
}
