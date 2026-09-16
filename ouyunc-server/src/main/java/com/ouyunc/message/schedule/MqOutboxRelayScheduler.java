package com.ouyunc.message.schedule;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.domain.entity.MqOutboxEntity;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.mq.core.api.MqPublisher;
import com.ouyunc.repository.support.MqOutboxSupport;
import com.ouyunc.repository.support.RepositorySupports;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * IM 进程内 MQ Outbox 补发（默认关闭；与 im-consumer Job 可并存，认领 CAS 互斥）。
 * <p>直连 {@link MqPublisher}，失败走 {@link MqOutboxSupport#markRetryOrDead}，禁止再入队造成循环。</p>
 * <p>集群开启后每轮 {@code tryLock(wait=0)}：未抢到跳过扫描，不阻塞 SYSTEM 定时线程；发送唯一性仍靠 {@code tryClaim}。</p>
 */
public final class MqOutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(MqOutboxRelayScheduler.class);

    private static final long SEND_TIMEOUT_SECONDS = 15L;

    private MqOutboxRelayScheduler() {
    }

    /**
     * 服务启动后按配置决定是否注册固定间隔扫描。
     */
    public static void start() {
        if (!MessageServerContext.serverProperties().isMqOutboxRelayEnable()) {
            log.info("MQ Outbox 进程内补发未开启（ouyunc.message.mq-outbox-relay.enable=false），由 im-consumer 负责");
            return;
        }
        ScheduleTimer.schedule(
                MessageConstant.MQ_OUTBOX_RELAY_TASK_ID,
                wrapper -> relayOnce(),
                MessageConstant.MQ_OUTBOX_RELAY_INITIAL_DELAY_MS,
                MessageConstant.MQ_OUTBOX_RELAY_PERIOD_MS,
                TimeUnit.MILLISECONDS,
                true,
                NumberConstant.NUMBER_NEGATIVE_1,
                TimerTaskKind.SYSTEM);
        log.info("MQ Outbox 补发任务已启动 taskId={} cluster={}",
                MessageConstant.MQ_OUTBOX_RELAY_TASK_ID,
                MessageServerContext.serverProperties().isClusterEnable());
    }

    private static void relayOnce() {
        if (MessageServerContext.serverProperties().isClusterEnable()) {
            relayOnceWithClusterLock();
            return;
        }
        doRelay();
    }

    /**
     * 集群下只允许一个节点扫描。wait=0 未抢到立即返回；lease 走 Redisson 看门狗，覆盖本轮发送耗时。
     */
    private static void relayOnceWithClusterLock() {
        RedissonClient redisson = MessageServerContext.redissonClient;
        if (redisson == null) {
            log.warn("集群已开启但 Redisson 不可用，跳过本轮 MQ Outbox 扫描");
            return;
        }
        RLock lock = redisson.getLock(CacheConstant.buildMqOutboxRelayLockCacheKey());
        boolean locked = false;
        try {
            locked = lock.tryLock(MessageConstant.MQ_OUTBOX_RELAY_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                return;
            }
            doRelay();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("MQ Outbox 集群锁异常: {}", e.getMessage());
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                } catch (Exception e) {
                    log.warn("MQ Outbox 释放锁失败: {}", e.getMessage());
                }
            }
        }
    }

    private static void doRelay() {
        MqOutboxSupport outbox = RepositorySupports.MQ_OUTBOX;
        MqPublisher publisher = RepositorySupports.INFRA.mqPublisher;
        if (publisher == null) {
            return;
        }
        List<MqOutboxEntity> due = outbox.listDuePending(MessageConstant.MQ_OUTBOX_RELAY_BATCH_SIZE);
        if (due == null || due.isEmpty()) {
            return;
        }
        for (MqOutboxEntity row : due) {
            if (row == null || row.getId() == null || StringUtils.isBlank(row.getTopic())
                    || StringUtils.isBlank(row.getPayload())) {
                continue;
            }
            if (!outbox.tryClaim(row.getId())) {
                continue;
            }
            republish(outbox, publisher, row);
        }
    }

    private static void republish(MqOutboxSupport outbox, MqPublisher publisher, MqOutboxEntity row) {
        try {
            String key = StringUtils.isNotBlank(row.getMqKey()) ? row.getMqKey() : null;
            publisher.send(row.getTopic(), key, row.getPayload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            outbox.deleteOnSuccess(row.getId());
            if (log.isDebugEnabled()) {
                log.debug("MQ Outbox 补发成功 id={} topic={} packetId={}",
                        row.getId(), row.getTopic(), row.getPacketId());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            outbox.markRetryOrDead(row, "补发被中断");
        } catch (Exception ex) {
            log.warn("MQ Outbox 补发失败 id={} topic={}: {}", row.getId(), row.getTopic(), ex.getMessage());
            outbox.markRetryOrDead(row, ex.getMessage());
        }
    }
}
