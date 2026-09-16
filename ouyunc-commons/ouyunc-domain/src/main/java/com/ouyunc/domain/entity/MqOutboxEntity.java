package com.ouyunc.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MQ 旁路失败 Outbox（MySQL）。
 * <p>与 Redis 热数据无关；仅在 MQ 发送失败后写入，供服务端补发。</p>
 */
@TableName("ouyunc_im_mq_outbox")
public class MqOutboxEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String topic;
    private String mqKey;
    private String bizKey;
    private Long packetId;
    private String payload;
    private Integer status;
    private Integer retryCount;
    private Long nextRetryAt;
    private String lastError;
    private String failureContext;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static final class Fields {
        public static final String id = "id";
        public static final String topic = "topic";
        public static final String mqKey = "mq_key";
        public static final String bizKey = "biz_key";
        public static final String packetId = "packet_id";
        public static final String payload = "payload";
        public static final String status = "status";
        public static final String retryCount = "retry_count";
        public static final String nextRetryAt = "next_retry_at";
        public static final String lastError = "last_error";
        public static final String failureContext = "failure_context";
        public static final String createTime = "create_time";
        public static final String updateTime = "update_time";
        public static final String now = "now";
        public static final String limit = "limit";
        public static final String staleBefore = "stale_before";
        public static final String sendingStatus = "sending_status";
        public static final String pendingStatus = "pending_status";
        public static final String sentStatus = "sent_status";
        public static final String deadStatus = "dead_status";
        public static final String maxRetry = "max_retry";
        public static final String backoffBase = "backoff_base";
        public static final String backoffMax = "backoff_max";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getMqKey() {
        return mqKey;
    }

    public void setMqKey(String mqKey) {
        this.mqKey = mqKey;
    }

    public String getBizKey() {
        return bizKey;
    }

    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }

    public Long getPacketId() {
        return packetId;
    }

    public void setPacketId(Long packetId) {
        this.packetId = packetId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Long getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Long nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String getFailureContext() {
        return failureContext;
    }

    public void setFailureContext(String failureContext) {
        this.failureContext = failureContext;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
