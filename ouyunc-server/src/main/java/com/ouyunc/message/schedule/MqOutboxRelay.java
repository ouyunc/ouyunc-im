package com.ouyunc.message.schedule;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MqOutboxStatus;
import com.ouyunc.domain.entity.MqOutboxEntity;
import com.ouyunc.mq.core.MqHeaderKeys;
import com.ouyunc.repository.support.RepositorySupports;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MQ Outbox 补发：扫描 MySQL PENDING → 发 MQ → 成功删行 / 失败退避或死信。
 * <p>不写 Redis；多节点靠 UPDATE 认领防双发（至少一次，消费端需幂等）。</p>
 */
public final class MqOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(MqOutboxRelay.class);

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private MqOutboxRelay() {
    }

    public static void start() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        ScheduleTimer.scheduleWithFixedDelay(
                MessageConstant.MQ_OUTBOX_RELAY_TASK_ID,
                wrapper -> relayOnce(),
                MessageConstant.MQ_OUTBOX_RELAY_INITIAL_DELAY_MS,
                MessageConstant.MQ_OUTBOX_RELAY_PERIOD_MS,
                TimeUnit.MILLISECONDS);
        log.info("MQ Outbox relay 已启动 periodMs={}", MessageConstant.MQ_OUTBOX_RELAY_PERIOD_MS);
    }

    static void relayOnce() {
        List<MqOutboxEntity> due = RepositorySupports.MQ_OUTBOX.listDuePending(MessageConstant.MQ_OUTBOX_RELAY_BATCH_SIZE);
        if (due == null || due.isEmpty()) {
            return;
        }
        for (MqOutboxEntity row : due) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (!RepositorySupports.MQ_OUTBOX.tryClaim(row.getId())) {
                continue;
            }
            republish(row);
        }
    }

    private static void republish(MqOutboxEntity row) {
        try {
            Map<String, Object> headers = new HashMap<>(2);
            if (row.getPacketId() != null) {
                headers.put(MqHeaderKeys.CORRELATION_ID, row.getPacketId());
            }
            if (StringUtils.isNotBlank(row.getMqKey())) {
                headers.put(MqHeaderKeys.MESSAGE_KEY, row.getMqKey());
            }
            RepositorySupports.INFRA.mqPublisher
                    .send(row.getTopic(), row.getMqKey(), row.getPayload(), headers.isEmpty() ? null : headers)
                    .whenComplete((ignored, ex) -> {
                        if (ex != null) {
                            RepositorySupports.MQ_OUTBOX.markRetryOrDead(row, ex.getMessage());
                            return;
                        }
                        RepositorySupports.MQ_OUTBOX.deleteOnSuccess(row.getId());
                        if (log.isDebugEnabled()) {
                            log.debug("MQ Outbox 补发成功 id={} topic={} statusWas={}",
                                    row.getId(), row.getTopic(), MqOutboxStatus.SENDING);
                        }
                    });
        } catch (Exception ex) {
            RepositorySupports.MQ_OUTBOX.markRetryOrDead(row, ex.getMessage());
        }
    }
}
