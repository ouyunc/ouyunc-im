package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.exception.ExceptionReporter;
import com.ouyunc.message.context.MessageServerContext;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 在当前业务线程执行需要分布式锁保护的逻辑。
 * 入站路径已经过 {@link ChannelOrderedTasks} 离开 EventLoop，禁止再投递到线程池，否则有序队列会在锁内业务完成前跑下一条。
 */
public final class DistributedLockHelper {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockHelper.class);

    private static final String SCENE = "DistributedLockHelper.runWithLock";

    private DistributedLockHelper() {
    }

    /**
     * @param lockKey      分布式锁 key
     * @param errorCode    锁内业务异常时上报的错误码
     * @param lockedAction 锁内执行的业务逻辑
     */
    public static void runWithLock(Packet packet, String lockKey, ExceptionCodeEnum errorCode, Runnable lockedAction) {
        RLock lock = MessageServerContext.redissonClient.getLock(lockKey);
        try {
            // 不传 lease：Redisson 看门狗续期，避免关系更新未完成锁已过期
            if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, TimeUnit.SECONDS)) {
                try {
                    lockedAction.run();
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                log.error("获取分布式锁超时, lockKey={}, packet={}", lockKey, packet);
                ExceptionReporter.reportSystem(ExceptionCodeEnum.ACQUIRE_LOCK_ERROR, "获取分布式锁超时", SCENE, packet);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("分布式锁等待被中断, lockKey={}", lockKey);
        } catch (Exception e) {
            log.error("分布式锁内业务异常, lockKey={}, 原因: {}", lockKey, e.getMessage(), e);
            ExceptionReporter.reportSystem(errorCode, "锁内业务异常: " + e.getMessage(), SCENE, packet, e);
        }
    }
}
