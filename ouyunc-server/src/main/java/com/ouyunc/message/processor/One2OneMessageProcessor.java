package com.ouyunc.message.processor;

import com.google.common.collect.Sets;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.validator.*;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.ouyunc.message.context.MessageServerContext.redissonClient;


/**
 * 一对一（单聊）消息处理器
 */
public class One2OneMessageProcessor extends AbstractMessageProcessor<Byte>{
    private static final Logger log = LoggerFactory.getLogger(One2OneMessageProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.ONE_2_ONE;
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
                // 校验是否被拉黑,如果被拉黑 （无论是否是好友，都可以拉黑）
                if (PermissionValidator.INSTANCE.negate().or(FriendValidator.INSTANCE.negate()).or(BlackListValidator.INSTANCE).or(FriendShieldValidator.INSTANCE).verify(packet, ctx)) {
                    log.warn("权限不足/在黑名单中/被屏蔽, 请知悉。该消息 {} 被忽略", packet);
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
     * 处理一对一消息
     */
    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.debug("Processing one-to-one message...");
        // 1. 尝试使用内容处理器
        if (processWithContentProcessor(ctx, packet)) {
            return;
        }
        // 2. 保存消息， 无论什么类型的消息，只要建立起好友关系，都需要往会话中保存一份消息，方便后续使用
        if (!saveMessage(packet)) {
            return;
        }
        // 3. 处理特殊消息类型
        int contentType = packet.getMessage().getContentType();
        if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType) {
            handleWithdrawMessage(ctx, packet);
        } else if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == contentType) {
            handleReadReceipt(ctx, packet);
        } else {
            deliverAndFireNext(ctx, packet);
        }
    }


    /**
     * 处理消息内容类型是撤回消息
     * @param ctx
     * @param packet
     */
    private void handleWithdrawMessage(ChannelHandlerContext ctx, Packet packet) {
        String sessionId = IdentityUtil.sessionId(packet.getMessage().getFrom(), packet.getMessage().getTo());
        handleLockedOperation(ctx, packet,
                ()-> repository().validWithdrawMessage(packet, sessionId, true),
                ()-> repository().savePacket2Mq(MqConstant.KAFKA_WITHDRAW_MESSAGE_TOPIC, packet),
                ()-> repository().withdrawMessage(packet, sessionId, Sets.newHashSet(packet.getMessage().getTo())),
                "一对一撤回消息", ExceptionCodeEnum.WITHDRAW_MESSAGE_ERROR);
    }

    /**
     * 处理消息内容类型是撤回消息
     * @param ctx
     * @param packet
     */
    private void handleReadReceipt(ChannelHandlerContext ctx, Packet packet) {
        handleLockedOperation(ctx, packet,
                ()-> repository().validReadReceiptMessage(packet, IdentityUtil.sessionId(packet.getMessage().getFrom(), packet.getMessage().getTo()), true),
                ()-> repository().savePacket2Mq(MqConstant.KAFKA_READ_RECEIPT_MESSAGE_TOPIC, packet),
                ()-> repository().readReceiptMessage(packet, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP),
                "一对一已读回执消息", ExceptionCodeEnum.READ_RECEIPT_MESSAGE_ERROR);
    }



    // 定义公共处理模板
    private void handleLockedOperation(ChannelHandlerContext ctx, Packet packet,
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
                deliverAndFireNext(ctx, packet);
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
    private void deliverAndFireNext(ChannelHandlerContext ctx, Packet packet) {
        deliverMessage(packet);
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
     * 保存消息
     */
    private boolean saveMessage(Packet packet) {
        Message message = packet.getMessage();
        String sessionId = IdentityUtil.sessionId(message.getFrom(), message.getTo());
        if (!repository().saveMessage(packet, sessionId, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)) {
            log.error("Failed to save one-to-one message: {}", packet);
            MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "保存一对一消息异常!", packet), true);
            return false;
        }
        return true;
    }


    /**
     * 发送消息给接收方
     */
    private void deliverMessage(Packet packet) {
        Message message = packet.getMessage();
        List<LoginClientInfo> toLoginClientInfos =
                ClientHelper.onlineAll(message.getMetadata().getAppKey(), message.getTo());

        if (CollectionUtils.isEmpty(toLoginClientInfos)) {
            log.warn("Recipient {} is offline, message stored", message.getTo());
            return;
        }
        MessageHelper.asyncSendMessage(packet, toLoginClientInfos);
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
}
