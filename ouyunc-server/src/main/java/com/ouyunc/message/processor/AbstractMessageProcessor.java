package com.ouyunc.message.processor;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.helper.MessageRefHelper;
import com.ouyunc.message.validator.AuthValidator;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * @Author fzx
 * @Description: 消息抽象处理类
 **/
public abstract class AbstractMessageProcessor<T extends Number> extends AbstractBaseProcessor<T> {
    private static final Logger log = LoggerFactory.getLogger(AbstractMessageProcessor.class);


    /**
     * 线程池事件执行器
     */
    protected ExecutorService messageProcessorExecutor() {
        return ThreadPoolManager.messageProcessorExecutor();
    }
    /**
     * 获取数据存储实现类, 子类可以重写来实现自定义存储实现
     */
    @SuppressWarnings("unchecked")
    public DefaultRepository repository() {
        return DefaultRepository.INSTANCE;
    }

    /**
     * @Author fzx
     * @Description 前置处理器，做认证授权相关处理，在真正处理消息前处理
     */
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 异步存储packet（目前只是保存相关信息，不做扩展，以后可以做数据分析使用），这里将该数据存储到时序数据库中
        repository().save(packet).whenComplete((saveResult, ex)->{
            if (ex == null) {
                // 发送成功，然后校验并传递给下个处理器处理
                if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
                    // 关闭当前 channel，这里会触发 DefaultSocketChannelInitializer 中的关闭逻辑
                    log.error("校验消息: {} 中的发送方登录认证失败,开始关闭channel", packet);
                    MessageContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过", packet), MessageEventTypeEnum.EXCEPTION), true);
                    ctx.close();
                    return;
                }
                // 校验是否拥有相关权限 permission

                // 做qos 处理（QOS_DUP 展开时在同一 packet 引用上原地更新）
                if (MessageContext.isQosEnable() && qosPreHandle(ctx, packet)) {
                    return;
                }
                ctx.fireChannelRead(packet);
            } else {
                // 发送失败
                log.error("Failed to send message: {} " , ex.getMessage());
                MessageContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, "通过发送mq保存消息异常!", packet), MessageEventTypeEnum.EXCEPTION), true);
            }
        });
    }


    /**
     * @Author fzx
     * @Description 传递处理器，仅做了一层包装，交给下个处理器去处理
     */
    protected void fireProcess(ChannelHandlerContext ctx, Packet packet, BiConsumer<ChannelHandlerContext, Packet> function) {
        function.accept(ctx, packet);
        // 交给下个处理器去处理
        ctx.fireChannelRead(packet);
    }

    /**
     * @Author fzx
     * @Description 做后置处理：仅传递给下个 handler。
     *
     * <p><strong>注意</strong>：QoS ACK 不再在此发送。子类应在持久化成功后显式调用 {@link #qosPostHandle(io.netty.channel.ChannelHandlerContext, com.ouyunc.base.packet.Packet)}，
     * 以避免「ACK 已回但消息未持久化」导致客户端不重发而消息丢失的问题。</p>
     */
    public void postProcess(ChannelHandlerContext ctx, Packet packet) {
        ctx.fireChannelRead(packet);
    }

    /**
     * 持久化成功后调用：发送 QoS ACK（如启用）。供子类在 saveMessage 回调内显式触发。
     */
    protected void sendQosAckAfterPersist(ChannelHandlerContext ctx, Packet packet) {
        if (MessageContext.isQosEnable()) {
            qosPostHandle(ctx, packet);
        }
    }

    /**
     * 消息已落库后向在线端推送，并交给后续处理器。
     */
    protected void deliverOnlineAndFireNext(ChannelHandlerContext ctx, Packet packet, String appKey, Collection<String> identities) {
        if (CollectionUtils.isNotEmpty(identities)) {
            for (String identity : identities) {
                if (StringUtils.isBlank(identity)) {
                    continue;
                }
                List<LoginClientInfo> clients = ClientHelper.onlineAll(appKey, identity);
                if (CollectionUtils.isNotEmpty(clients)) {
                    MessageHelper.asyncSendMessage(packet, clients);
                }
            }
        }
        ctx.fireChannelRead(packet);
    }

    protected boolean qosPreHandleIfEnabled(ChannelHandlerContext ctx, Packet packet) {
        return MessageContext.isQosEnable() && qosPreHandle(ctx, packet);
    }

    protected void publishCachePersistenceError(Packet packet, String detail) {
        MessageServerContext.publishEvent(new MessageEvent(
                ExceptionEventPayload.of(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, detail, packet),
                MessageEventTypeEnum.EXCEPTION), true);
    }

    protected boolean isReadReceipt(Packet packet) {
        return packet != null
                && packet.getMessage() != null
                && MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == packet.getMessage().getContentType();
    }

    protected void completeReadReceipt(ChannelHandlerContext ctx, Packet packet, Runnable delivery) {
        if (delivery != null) {
            delivery.run();
        }
        ctx.fireChannelRead(packet);
    }

    /**
     * 在业务线程池中执行需要分布式锁保护的逻辑，避免在 Netty EventLoop 上阻塞等待锁。
     *
     * <p>典型用法：</p>
     * <pre>
     * runWithDistributedLock(ctx, packet, lockKey, ExceptionCodeEnum.BIND_FRIEND_ERROR,
     *     () -> {
     *         // 锁内业务逻辑
     *     });
     * </pre>
     *
     * @param ctx          channel 上下文
     * @param packet       消息包（用于异常上报）
     * @param lockKey      分布式锁 key
     * @param errorCode    获取锁失败/异常时上报的错误码
     * @param lockedAction 锁内执行的业务逻辑
     */
    protected void runWithDistributedLock(ChannelHandlerContext ctx, Packet packet,
                                          String lockKey, ExceptionCodeEnum errorCode,
                                          Runnable lockedAction) {
        ThreadPoolManager.messageProcessorExecutor().execute(() -> {
            RLock lock = MessageServerContext.redissonClient.getLock(lockKey);
            try {
                if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                    try {
                        lockedAction.run();
                    } finally {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                    }
                } else {
                    log.error("获取分布式锁超时, lockKey={}, packet={}", lockKey, packet);
                    MessageServerContext.publishEvent(new MessageEvent(
                            ExceptionEventPayload.of(ExceptionCodeEnum.ACQUIRE_LOCK_ERROR, "获取分布式锁超时", packet),
                            MessageEventTypeEnum.EXCEPTION), true);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("分布式锁等待被中断, lockKey={}", lockKey);
            } catch (Exception e) {
                log.error("分布式锁内业务异常, lockKey={}, 原因: {}", lockKey, e.getMessage(), e);
                MessageServerContext.publishEvent(new MessageEvent(
                        ExceptionEventPayload.of(errorCode, "锁内业务异常: " + e.getMessage(), packet),
                        MessageEventTypeEnum.EXCEPTION), true);
            }
        });
    }

    /** 校验 message.ref（packetId 列表，最多 {@link MessageConstant#MAX_REF_COUNT} 条） */
    protected boolean normalizeMessageRefOrReject(Packet packet) {
        Message message = packet.getMessage();
        if (message == null || CollectionUtils.isEmpty(message.getRef())) {
            return true;
        }
        try {
            message.setRef(MessageRefHelper.normalizeAndValidate(message.getRef()));
            return true;
        } catch (IllegalArgumentException ex) {
            log.warn("消息引用校验失败: {} | packet={}", ex.getMessage(), packet);
            MessageServerContext.publishEvent(new MessageEvent(
                    ExceptionEventPayload.of(ExceptionCodeEnum.MESSAGE_REF_INVALID_ERROR, ex.getMessage(), packet),
                    MessageEventTypeEnum.EXCEPTION), true);
            return false;
        }
    }
}
