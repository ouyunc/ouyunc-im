package com.ouyunc.id.config;

import java.time.Duration;

/** 发号协议与守护参数。位宽和 epoch 属于存储协议，禁止在滚动部署时随意调整。 */
public final class IdGeneratorConstants {
    public static final String DEFAULT_NAMESPACE = "OUYUNC";
    public static final String NAMESPACE_PROPERTY = "ouyunc.id.namespace";
    public static final String NAMESPACE_ENV = "OUYUNC_ID_NAMESPACE";
    public static final String GENERATOR_NAME = "snowflake";
    public static final int TIMESTAMP_BITS = 41;
    public static final int MACHINE_BITS = 10;
    public static final int SEQUENCE_BITS = 12;
    public static final int ID_WIDTH = 19;
    public static final int CLOCK_SPIN_THRESHOLD_MS = 10;
    public static final int CLOCK_BROKEN_THRESHOLD_MS = 2000;
    public static final Duration SAFE_GUARD_DURATION = Duration.ofMinutes(5);
    public static final long GUARD_INTERVAL_SECONDS = 100;
    // 早于 Redis 的回收窗口停止发号，预留调度、网络和有限时钟误差余量。
    public static final Duration LOCAL_VALID_DURATION = Duration.ofSeconds(200);
    private IdGeneratorConstants() { }
}
