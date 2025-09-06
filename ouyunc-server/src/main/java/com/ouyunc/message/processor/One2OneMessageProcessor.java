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
import com.ouyunc.domain.constants.IdentityType;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.validator.*;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RLockReactive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.ouyunc.message.context.MessageServerContext.reactiveRedissonClient;


/**
 * 一对一（单聊）消息处理器;
 * 如果在使用过程中存在使用redis 的瓶颈（吞出量） 可以使用 响应式redis 进行改造提高吞吐量 CacheFactory.REACTIVE_REDIS.instance()
 */
public final class One2OneMessageProcessor extends AbstractMessageProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(One2OneMessageProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.ONE_2_ONE;
    }


    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 异步存储packet（目前只是保存相关信息，不做扩展，以后可以做数据分析使用），这里将该数据存储到时序数据库中
        repository().save(packet).whenComplete((sendResult, ex) -> {
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
                // 构建校验逻辑
                PermissionValidator.INSTANCE.negate()
                        .or(FriendValidator.INSTANCE.negate())
                        .or(BlackListValidator.INSTANCE)
                        .or(FriendShieldValidator.INSTANCE)
                        .or(FromToValidator.INSTANCE)
                        .verify(packet, ctx)
                        .onErrorResume(error -> {
                            log.error("校验过程中出现异常: {}", error.getMessage());
                            return Mono.just(true); // 出现异常时默认校验不通过
                        }).flatMap(result -> {
                            if (result) {
                                log.warn("权限不足/不是好友/在黑名单中/被屏蔽/发送方和接收方相同, 请知悉。该消息 {} 被忽略", packet);
                                return Mono.empty(); // 校验不通过，不传递消息
                            }
                            return Mono.just(packet); // 校验通过，继续传递消息
                        }).subscribe(ctx::fireChannelRead);
            } else {
                // 发送失败
                log.error("Failed to send message: {} ", ex.getMessage());
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
        saveMessage(packet).subscribe(result -> {
            // 保存成功处理后续逻辑
            if (result) {
                // 3. 处理特殊消息类型
                int contentType = packet.getMessage().getContentType();
                if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType) {
                    handleWithdrawMessage(ctx, packet);
                } else if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == contentType) {
                    handleReadReceipt(ctx, packet);
                } else {
                    deliverAndFireNext(ctx, packet);
                }
            }else {
                log.error("one 2 one 保存消息失败: {}", packet);
            }
        });
    }


    /**
     * 处理消息内容类型是撤回消息
     *
     * @param ctx
     * @param packet
     */
    private void handleWithdrawMessage(ChannelHandlerContext ctx, Packet packet) {
        String from = packet.getMessage().getFrom();
        String to = packet.getMessage().getTo();
        String sessionId = IdentityUtil.sessionId(from, to);
        reactiveHandleLockedOperation(ctx, packet,
                repository().reactiveValidWithdrawMessage(packet, sessionId, true),
                ()-> repository().savePacket2Mq(MqConstant.KAFKA_WITHDRAW_MESSAGE_TOPIC, sessionId, packet),
                repository().reactiveWithdrawMessage(packet, sessionId, Sets.newHashSet(to)),
                "一对一撤回消息", ExceptionCodeEnum.WITHDRAW_MESSAGE_ERROR);
    }

    /**
     * 处理消息内容类型是撤回消息
     *
     * @param ctx
     * @param packet
     */
    private void handleReadReceipt(ChannelHandlerContext ctx, Packet packet) {
        String sessionId = IdentityUtil.sessionId(packet.getMessage().getFrom(), packet.getMessage().getTo());
        reactiveHandleLockedOperation(ctx, packet,
                repository().reactiveValidReadReceiptMessage(packet, sessionId, true),
                () -> repository().savePacket2Mq(MqConstant.KAFKA_READ_RECEIPT_MESSAGE_TOPIC, sessionId, packet),
                repository().reactiveReadReceiptMessage(packet, IdentityType.ONE_2_ONE, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP),
                "一对一已读回执消息", ExceptionCodeEnum.READ_RECEIPT_MESSAGE_ERROR);
    }


    /**
     * 响应式处理加锁逻辑
     *
     * @param ctx
     * @param packet
     * @param validator
     * @param mqSender
     * @param processor
     * @param errorLog
     * @param errorCode
     */
    private void reactiveHandleLockedOperation(ChannelHandlerContext ctx, Packet packet,
                                               Mono<Boolean> validator,
                                               Supplier<CompletableFuture<?>> mqSender,
                                               Mono<Boolean> processor,
                                               String errorLog, ExceptionCodeEnum errorCode) {
        Message message = packet.getMessage();
        String sessionId = IdentityUtil.sessionId(message.getFrom(), message.getTo());
        RLockReactive lock = createMultiLock(message.getMetadata().getAppKey(), sessionId, message.getTo());
        lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)
                .flatMap(locked -> {
                    if (!locked) {
                        MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.ACQUIRE_LOCK_ERROR, errorLog + "获取锁失败", packet), true);
                        return Mono.empty();
                    }
                    return validator.flatMap(valid -> {
                        if (!valid) {
                            MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.ACQUIRE_LOCK_ERROR, errorLog + "验证失败", packet), true);
                            return Mono.empty();
                        }
                        return Mono.fromFuture(mqSender.get())
                                .onErrorResume(ex -> {
                                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.ACQUIRE_LOCK_ERROR, errorLog + "发送mq持久化异常：" + ex.getMessage(), packet), true);
                                    return Mono.empty();
                                })
                                .then(processor)
                                .flatMap(processed -> {
                                    if (processed) {
                                        deliverAndFireNext(ctx, packet);
                                    } else {
                                        MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.ACQUIRE_LOCK_ERROR, errorLog + "业务处理失败", packet), true);
                                    }
                                    return Mono.empty();
                                }).onErrorResume(ex -> {
                                    publishExceptionEvent(errorCode, errorLog + "处理过程中发生异常", packet);
                                    return Mono.empty();
                                });
                    }).publishOn(Schedulers.boundedElastic()).doFinally(s -> lock.unlock()
                            .doOnError(e -> log.error("解锁失败", e))
                            .onErrorResume(e -> {
                                log.error("解锁异常: {}", e.getMessage());
                                MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.UN_LOCK_ERROR, errorLog + "解锁失败", packet), true);
                                return Mono.empty();
                            }).subscribe());
                }).subscribe();
    }


    private void publishExceptionEvent(ExceptionCodeEnum code, String msg, Packet packet) {
        MessageServerContext.publishEvent(
                new ExceptionEvent(code, msg, packet), true);
    }

    /**
     * 发送消息给接收方
     *
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
    private Mono<Boolean> saveMessage(Packet packet) {
        Message message = packet.getMessage();
        String sessionId = IdentityUtil.sessionId(message.getFrom(), message.getTo());
        return repository().reactiveSaveMessage(packet, sessionId, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP);
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


    private RLockReactive createMultiLock(String appKey, String sessionId, String to) {
        return reactiveRedissonClient.getMultiLock(
                reactiveRedissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.SESSION + sessionId),
                reactiveRedissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.OFFLINE + to)
        );
    }

}
