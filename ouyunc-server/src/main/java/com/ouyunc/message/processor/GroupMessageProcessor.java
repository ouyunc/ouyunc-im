package com.ouyunc.message.processor;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
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
import java.util.concurrent.CompletableFuture;
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
        if (!groupUserIdentitySet.remove(packet.getMessage().getFrom())) {
            log.error("发送方：{}, 不在群组：{} 中！群消息： {}", packet.getMessage().getFrom(), packet.getMessage().getTo(), packet);
            MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.GROUP_MEMBER_NOT_EXIST_ERROR, "发送者不在群组中", packet), true);
            return;
        }
        // 3. 保存消息
        if (!saveGroupMessage(packet, groupUserIdentitySet)) {
            return;
        }
        // 4. 处理特殊消息类型
        int contentType = packet.getMessage().getContentType();
        if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType) {
            handleWithdrawMessage(ctx, packet, groupUserIdentitySet);
        } else if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == contentType) {
            handleReadReceipt(ctx, packet, groupUserIdentitySet);
        } else {
            deliverAndFireNext(ctx, packet, groupUserIdentitySet);
        }
    }


    /**
     * 处理消息内容类型是撤回消息
     * @param ctx
     * @param packet
     */
    private void handleReadReceipt(ChannelHandlerContext ctx, Packet packet, Set<String> groupUserIdentitySet) {
        String sessionId = packet.getMessage().getTo();
        handleLockedOperation(ctx, packet,groupUserIdentitySet,
                ()-> repository().validReadReceiptMessage(packet, packet.getMessage().getTo(), true),
                ()-> repository().savePacket2Mq(MqConstant.KAFKA_READ_RECEIPT_MESSAGE_TOPIC, sessionId, packet),
                ()-> repository().readReceiptMessage(packet, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP),
                "已读回执消息", ExceptionCodeEnum.READ_RECEIPT_MESSAGE_ERROR);
    }


    /**
     * 处理消息内容类型是撤回消息
     * @param ctx
     * @param packet
     */
    private void handleWithdrawMessage(ChannelHandlerContext ctx, Packet packet, Set<String> groupUserIdentitySet) {
        String sessionId = packet.getMessage().getTo();
        // 获取当前撤销人员是否是群主或者管理员，他们是最大权限可以撤销所有成员的消息，当然也包括自己
        Set<String> leaderOrManagerIdentitySet = repository().groupManagerAndLeaderUsersIdentity(packet);
        boolean leaderOrManager = CollectionUtils.isNotEmpty(leaderOrManagerIdentitySet) && leaderOrManagerIdentitySet.contains(packet.getMessage().getFrom());
        handleLockedOperation(ctx, packet,groupUserIdentitySet,
                ()-> repository().validWithdrawMessage(packet, sessionId, leaderOrManager),
                ()-> repository().savePacket2Mq(MqConstant.KAFKA_WITHDRAW_MESSAGE_TOPIC, sessionId, packet),
                ()-> repository().withdrawMessage(packet, sessionId, groupUserIdentitySet),
                "撤回消息", ExceptionCodeEnum.WITHDRAW_MESSAGE_ERROR);
    }


    // 定义公共处理模板
    private void handleLockedOperation(ChannelHandlerContext ctx, Packet packet,Set<String> groupUserIdentitySet,
                                       Supplier<Boolean>  validator,
                                       Supplier<CompletableFuture<?>> mqSender,
                                       Supplier<Boolean>  processor,
                                       String errorLog, ExceptionCodeEnum errorCode) {
        Message message = packet.getMessage();
        String sessionId = IdentityUtil.sessionId(message.getFrom(), message.getTo());
        RLock multiLock = createMultiLock(message.getMetadata().getAppKey(), sessionId, message.getTo());
        try {
            if (!tryLock(multiLock)) {
                log.error("{}获取锁失败: {}", errorLog, packet);
                publishLockError(packet, errorLog + "获取锁失败");
                return;
            }
            if (!validator.get()) {
                log.error("{} 校验失败: {}", errorLog, packet);
                publishExceptionEvent(errorCode,errorLog + "校验失败", packet);
                return;
            }
            mqSender.get().whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("{}发送mq持久化异常: {}", errorLog, ex.getMessage());
                    publishExceptionEvent(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, errorLog + "发送mq持久化异常：" + ex.getMessage(), packet);
                }else if (!processor.get()) {
                    log.error("{} 处理失败: {}", errorLog, packet);
                    publishExceptionEvent(errorCode, errorLog + "处理失败", packet);
                }
                // 传递数据
                deliverAndFireNext(ctx, packet, groupUserIdentitySet);
            });
        } catch (Exception e) {
            log.error("{}处理消息异常: ", errorLog, e);
            publishExceptionEvent(errorCode,  errorLog + "处理消息异常" + e.getMessage(), packet);
        } finally {
            releaseLock(multiLock);
        }
    }
    private void publishExceptionEvent(ExceptionCodeEnum code, String msg, Packet packet) {
        MessageServerContext.publishEvent(
                new ExceptionEvent(code, msg, packet), true);
    }

    /**
     * 发送消息给接收方
     * @param packet
     * @param ctx
     */
    private void deliverAndFireNext(ChannelHandlerContext ctx, Packet packet, Set<String> groupUserIdentitySet) {
        // 4. 发送消息给接收方
        deliverMessage(packet, groupUserIdentitySet);
        // 处理成功则转到下个处理器
        ctx.fireChannelRead(packet);
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

    private void publishLockError(Packet packet, String message) {
        log.error("Failed to acquire lock: {}", packet);
        MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.ACQUIRE_LOCK_ERROR, message, packet), true);
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
