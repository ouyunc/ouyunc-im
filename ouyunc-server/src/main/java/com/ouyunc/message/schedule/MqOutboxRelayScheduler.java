package com.ouyunc.message.schedule;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.domain.entity.MqOutboxEntity;
import com.ouyunc.mq.core.api.MqPublisher;
import com.ouyunc.repository.support.MqOutboxSupport;
import com.ouyunc.repository.support.RepositorySupports;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * IM 进程内 MQ Outbox 补发（与 im-consumer Job 可并存，认领 CAS 互斥）。
 * <p>直连 {@link MqPublisher}，失败走 {@link MqOutboxSupport#markRetryOrDead}，禁止再入队造成循环。</p>
 */
public final class MqOutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(MqOutboxRelayScheduler.class);

    private static final long SEND_TIMEOUT_SECONDS = 15L;

    private MqOutboxRelayScheduler() {
    }

    /**
     * 服务启动后注册固定间隔扫描。
     */
    public static void start() {
        ScheduleTimer.schedule(
                MessageConstant.MQ_OUTBOX_RELAY_TASK_ID,
                wrapper -> relayOnce(),
                MessageConstant.MQ_OUTBOX_RELAY_INITIAL_DELAY_MS,
                MessageConstant.MQ_OUTBOX_RELAY_PERIOD_MS,
                TimeUnit.MILLISECONDS,
                true,
                NumberConstant.NUMBER_NEGATIVE_1,
                TimerTaskKind.SYSTEM);
        log.info("MQ Outbox 补发任务已启动 taskId={}", MessageConstant.MQ_OUTBOX_RELAY_TASK_ID);
    }

    private static void relayOnce() {
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
