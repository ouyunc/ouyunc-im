package com.ouyunc.base.model;

import com.ouyunc.base.constant.MessageConstant;

/**
 * 节点消息写入健康快照：仅汇总真实操作，不访问中间件、不写 Redis、不创建探测消息。
 * 用于写入告警及诊断，连接 readiness 独立运行，避免摘除全部节点后没有流量验证恢复。
 */
public final class MessageWriteHealth {
    public enum State { UNKNOWN, HEALTHY, DEGRADED, UNAVAILABLE }
    /** 固定维度，不能使用租户或消息 ID 动态创建观测项。 */
    public enum Stage {
        REDIS_MESSAGE(null), REDIS_IDEMPOTENCY(null), REDIS_UNREAD(null),
        MQ_ARCHIVE(com.ouyunc.base.constant.MqConstant.MQ_SAVE_MESSAGE_TOPIC),
        MQ_WITHDRAW(com.ouyunc.base.constant.MqConstant.MQ_WITHDRAW_MESSAGE_TOPIC),
        MQ_READ(com.ouyunc.base.constant.MqConstant.MQ_READ_RECEIPT_MESSAGE_TOPIC),
        MQ_FRIEND(com.ouyunc.base.constant.MqConstant.MQ_FRIEND_REQUEST_TOPIC),
        MQ_GROUP(com.ouyunc.base.constant.MqConstant.MQ_GROUP_REQUEST_TOPIC),
        MQ_EXTERNAL(com.ouyunc.base.constant.MqConstant.MQ_EXTERNAL_CHANNEL_OUTBOUND_TOPIC),
        MQ_ACTIVITY(com.ouyunc.base.constant.MqConstant.MQ_CS_TICKET_ACTIVITY_TOPIC);
        private final String topic;
        private final Dependency dependency = new Dependency();
        Stage(String topic) { this.topic = topic; }
    }
    private MessageWriteHealth() { }

    public static void redisResult(Stage stage, boolean success) {
        if (stage.topic != null) {
            throw new IllegalArgumentException("Redis observation requires a Redis stage");
        }
        stage.dependency.record(success);
    }

    /** 使用逻辑 Topic 匹配固定观测项，环境前缀不增加维度。 */
    public static void mqResult(String topic, boolean success) {
        String logical = com.ouyunc.base.constant.MqDestination.logical(topic);
        for (Stage stage : Stage.values()) {
            if (stage.topic != null && stage.topic.equals(logical)) {
                stage.dependency.record(success);
                return;
            }
        }
    }

    public static Snapshot snapshot() {
        long now = System.currentTimeMillis();
        java.util.List<StageObservation> stages = new java.util.ArrayList<>();
        for (Stage stage : Stage.values()) {
            stages.add(new StageObservation(stage, stage.dependency.snapshot(now)));
        }
        Observation redis = aggregate(stages, false);
        Observation mq = aggregate(stages, true);
        return new Snapshot(redis.state() == State.HEALTHY && mq.state() == State.HEALTHY,
                redis, mq, java.util.List.copyOf(stages));
    }

    /** 未发生过的可选业务不阻塞汇总；发生过故障的环节必须由本环节成功恢复。 */
    private static Observation aggregate(java.util.List<StageObservation> stages, boolean mq) {
        State state = State.UNKNOWN;
        int failures = 0;
        long successTime = 0;
        long failureTime = 0;
        boolean observed = false;
        for (StageObservation entry : stages) {
            Observation value = entry.observation();
            if ((entry.stage().topic != null) != mq
                    || value.lastSuccessTime() == 0 && value.lastFailureTime() == 0) {
                continue;
            }
            if (!observed || severity(value.state()) > severity(state)) {
                state = value.state();
            }
            observed = true;
            failures = Math.max(failures, value.consecutiveFailures());
            successTime = Math.max(successTime, value.lastSuccessTime());
            failureTime = Math.max(failureTime, value.lastFailureTime());
        }
        return new Observation(state, failures, successTime, failureTime);
    }

    private static int severity(State state) {
        return switch (state) {
            case HEALTHY -> 0;
            case UNKNOWN -> 1;
            case DEGRADED -> 2;
            case UNAVAILABLE -> 3;
        };
    }

    public record Snapshot(boolean ready, Observation redis, Observation mq,
                           java.util.List<StageObservation> stages) { }
    public record StageObservation(Stage stage, Observation observation) { }
    public record Observation(State state, int consecutiveFailures, long lastSuccessTime, long lastFailureTime) { }
    /** 按完成时刻汇总；故障不会因时间流逝被误判恢复，只有成功操作可以清除失败状态。 */
    private static final class Dependency {
        private int failures;
        private long successTime;
        private long failureTime;
        private synchronized void record(boolean success) {
            if (success) {
                failures = 0;
                successTime = System.currentTimeMillis();
            } else {
                failures = Math.min(MessageConstant.MESSAGE_WRITE_HEALTH_FAILURE_THRESHOLD, failures + 1);
                failureTime = System.currentTimeMillis();
            }
        }
        private synchronized Observation snapshot(long now) {
            State state;
            if (failures >= MessageConstant.MESSAGE_WRITE_HEALTH_FAILURE_THRESHOLD) {
                state = State.UNAVAILABLE;
            } else if (failures > 0) {
                state = State.DEGRADED;
            } else if (successTime == 0 || now - successTime > MessageConstant.MESSAGE_WRITE_HEALTH_FRESH_MS
                    || now < successTime) {
                state = State.UNKNOWN;
            } else {
                state = State.HEALTHY;
            }
            return new Observation(state, failures, successTime, failureTime);
        }
    }
}
