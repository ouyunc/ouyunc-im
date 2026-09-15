package com.ouyunc.repository.support;

import com.ouyunc.base.constant.JdbcSqlDialectHolder;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MqOutboxStatus;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.domain.entity.MqOutboxEntity;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * MQ 旁路失败 MySQL Outbox：写入、认领、重试、删除。
 * <p>Redis 零侵入；仅在 {@link MessageMqPublisherSupport} 发送失败后异步落库。</p>
 */
public final class MqOutboxSupport {

    private static final Logger log = LoggerFactory.getLogger(MqOutboxSupport.class);

    private final RepositoryInfrastructure infra;

    public MqOutboxSupport(RepositoryInfrastructure infra) {
        this.infra = infra;
    }

    /**
     * 异步入队 Outbox（仓库线程池），不阻塞 MQ 失败回调线程。
     */
    public void enqueueAsync(String topic, String mqKey, Long packetId, String payload, String failureContext, String lastError) {
        if (StringUtils.isBlank(topic) || StringUtils.isBlank(payload)) {
            log.warn("MQ Outbox 入队跳过：topic/payload 为空 topic={} packetId={}", topic, packetId);
            return;
        }
        try {
            infra.dbExecutor().execute(() -> enqueue(topic, mqKey, packetId, payload, failureContext, lastError));
        } catch (Exception ex) {
            log.error("MQ Outbox 提交异步入队失败 topic={} packetId={}", topic, packetId, ex);
        }
    }

    /**
     * 同步写入；同 topic+biz_key 冲突时刷新错误信息与 payload。
     */
    public void enqueue(String topic, String mqKey, Long packetId, String payload, String failureContext, String lastError) {
        long id = MessageContext.idGenerator().generateId();
        String bizKey = buildBizKey(packetId, mqKey, payload);
        long now = TimeUtil.currentTimeMillis();
        try {
            infra.jdbcClient.sql(JdbcSqlDialectHolder.insertMqOutbox())
                    .param(MqOutboxEntity.Fields.id, id)
                    .param(MqOutboxEntity.Fields.topic, topic)
                    .param(MqOutboxEntity.Fields.mqKey, blankToNull(mqKey))
                    .param(MqOutboxEntity.Fields.bizKey, bizKey)
                    .param(MqOutboxEntity.Fields.packetId, packetId)
                    .param(MqOutboxEntity.Fields.payload, payload)
                    .param(MqOutboxEntity.Fields.status, MqOutboxStatus.PENDING.getCode())
                    .param(MqOutboxEntity.Fields.retryCount, 0)
                    .param(MqOutboxEntity.Fields.nextRetryAt, now)
                    .param(MqOutboxEntity.Fields.lastError, truncate(lastError, 1024))
                    .param(MqOutboxEntity.Fields.failureContext, truncate(failureContext, 512))
                    .update();
        } catch (Exception ex) {
            log.error("MQ Outbox 写入失败 topic={} bizKey={} packetId={}", topic, bizKey, packetId, ex);
        }
    }

    /** 回收僵死 SENDING，再拉取到期 PENDING。 */
    public List<MqOutboxEntity> listDuePending(int limit) {
        resetStaleSending();
        long now = TimeUtil.currentTimeMillis();
        try {
            return infra.jdbcClient.sql(JdbcSqlDialectHolder.selectMqOutboxDue())
                    .param(MqOutboxEntity.Fields.status, MqOutboxStatus.PENDING.getCode())
                    .param(MqOutboxEntity.Fields.now, now)
                    .param(MqOutboxEntity.Fields.limit, Math.max(1, limit))
                    .query(MqOutboxEntity.class)
                    .list();
        } catch (Exception ex) {
            log.error("MQ Outbox 扫描失败", ex);
            return Collections.emptyList();
        }
    }

    /**
     * @return true 表示认领成功
     */
    public boolean tryClaim(long id) {
        try {
            int rows = infra.jdbcClient.sql(JdbcSqlDialectHolder.claimMqOutbox())
                    .param("sending_status", MqOutboxStatus.SENDING.getCode())
                    .param(MqOutboxEntity.Fields.id, id)
                    .param("pending_status", MqOutboxStatus.PENDING.getCode())
                    .update();
            return rows > 0;
        } catch (Exception ex) {
            log.error("MQ Outbox 认领失败 id={}", id, ex);
            return false;
        }
    }

    public void deleteOnSuccess(long id) {
        try {
            infra.jdbcClient.sql(JdbcSqlDialectHolder.deleteMqOutbox())
                    .param(MqOutboxEntity.Fields.id, id)
                    .update();
        } catch (Exception ex) {
            log.error("MQ Outbox 删除失败 id={}", id, ex);
        }
    }

    /**
     * 补发失败：未超限则 PENDING+退避；超限则 DEAD。
     */
    public void markRetryOrDead(MqOutboxEntity row, String error) {
        if (row == null || row.getId() == null) {
            return;
        }
        int nextRetry = row.getRetryCount() == null ? 1 : row.getRetryCount() + 1;
        int status;
        long nextAt;
        if (nextRetry >= MessageConstant.MQ_OUTBOX_MAX_RETRY) {
            status = MqOutboxStatus.DEAD.getCode();
            nextAt = TimeUtil.currentTimeMillis();
            log.error("MQ Outbox 超限死信 id={} topic={} packetId={} retry={}",
                    row.getId(), row.getTopic(), row.getPacketId(), nextRetry);
        } else {
            status = MqOutboxStatus.PENDING.getCode();
            nextAt = TimeUtil.currentTimeMillis() + backoffMs(nextRetry);
        }
        try {
            infra.jdbcClient.sql(JdbcSqlDialectHolder.updateMqOutboxRetry())
                    .param(MqOutboxEntity.Fields.status, status)
                    .param(MqOutboxEntity.Fields.retryCount, nextRetry)
                    .param(MqOutboxEntity.Fields.nextRetryAt, nextAt)
                    .param(MqOutboxEntity.Fields.lastError, truncate(error, 1024))
                    .param(MqOutboxEntity.Fields.id, row.getId())
                    .update();
        } catch (Exception ex) {
            log.error("MQ Outbox 更新重试状态失败 id={}", row.getId(), ex);
        }
    }

    public static long backoffMs(int retryCount) {
        int shift = Math.max(0, Math.min(retryCount, 16));
        long delay = MessageConstant.MQ_OUTBOX_BACKOFF_BASE_MS << shift;
        return Math.min(delay, MessageConstant.MQ_OUTBOX_BACKOFF_MAX_MS);
    }

    static String buildBizKey(Long packetId, String mqKey, String payload) {
        if (packetId != null && packetId > 0) {
            return String.valueOf(packetId);
        }
        if (StringUtils.isNotBlank(mqKey)) {
            return "k:" + mqKey;
        }
        return "h:" + sha256Hex(payload);
    }

    private void resetStaleSending() {
        try {
            LocalDateTime staleBefore = LocalDateTime.now(ZoneId.systemDefault())
                    .minusSeconds(Math.max(1L, MessageConstant.MQ_OUTBOX_SENDING_STALE_MS / 1000L));
            infra.jdbcClient.sql(JdbcSqlDialectHolder.resetStaleMqOutboxSending())
                    .param("pending_status", MqOutboxStatus.PENDING.getCode())
                    .param("sending_status", MqOutboxStatus.SENDING.getCode())
                    .param(MqOutboxEntity.Fields.staleBefore, staleBefore)
                    .update();
        } catch (Exception ex) {
            log.warn("MQ Outbox 回收僵死 SENDING 失败: {}", ex.getMessage());
        }
    }

    private static String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String sha256Hex(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(StringUtils.defaultString(payload).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(StringUtils.defaultString(payload).hashCode());
        }
    }
}
