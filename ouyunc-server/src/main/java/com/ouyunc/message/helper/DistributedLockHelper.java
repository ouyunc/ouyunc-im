package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 在业务线程池中执行需要分布式锁保护的逻辑，避免在 Netty EventLoop 上阻塞等待锁。
 */
public final class DistributedLockHelper {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockHelper.class);

    private DistributedLockHelper() {
    }

    /**
     * @param lockKey      分布式锁 key
     * @param errorCode    获取锁失败/异常时上报的错误码
     * @param lockedAction 锁内执行的业务逻辑
     */
    public static void runWithLock(Packet packet, String lockKey, ExceptionCodeEnum errorCode, Runnable lockedAction) {
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
}
