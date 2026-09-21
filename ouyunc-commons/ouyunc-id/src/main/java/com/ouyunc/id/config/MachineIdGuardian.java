package com.ouyunc.id.config;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 机器号守护及本地安全闸门。续约失败或期限耗尽后永久失效，必须重启重新分配。
 * 迟到的续约响应不能恢复已失效生成器；Redis 状态重置不在本地期限的保证范围内。
 */
public final class MachineIdGuardian {
    private static final Logger log = LoggerFactory.getLogger(MachineIdGuardian.class);
    private enum State { INITIALIZING, ACTIVE, UNSAFE, STOPPED }
    private final MachineIdDistributor distributor;
    private final String namespace;
    private final InstanceId instanceId;
    private final Duration safeGuardDuration;
    private final long intervalSeconds;
    private final ScheduledExecutorService scheduler;
    private State state = State.INITIALIZING;
    private long validUntilNanos;

    public MachineIdGuardian(MachineIdDistributor distributor, String namespace, InstanceId instanceId,
                             MachineState machineState, Duration safeGuardDuration, long intervalSeconds) {
        if (safeGuardDuration == null
                || safeGuardDuration.compareTo(IdGeneratorConstants.LOCAL_VALID_DURATION) <= 0
                || intervalSeconds <= 0
                || intervalSeconds >= IdGeneratorConstants.LOCAL_VALID_DURATION.toSeconds()) {
            throw new IllegalArgumentException("Renewal interval < local validity < Redis guard duration required");
        }
        this.distributor = distributor;
        this.namespace = namespace;
        this.instanceId = instanceId;
        this.safeGuardDuration = safeGuardDuration;
        this.intervalSeconds = intervalSeconds;
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "MachineIdGuardian-" + machineState.getMachineId());
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 启动必须得到一次成功续约，不能带故障接收流量。 */
    public void start() {
        guard();
        requireActive();
        scheduler.scheduleWithFixedDelay(this::guard, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    /** 发号前后校验，防止暂停后返回已经失去安全性的 ID。 */
    public synchronized void requireActive() {
        if (state == State.ACTIVE && System.nanoTime() - validUntilNanos >= 0) {
            invalidate("local deadline expired", null);
        }
        if (state != State.ACTIVE) {
            throw new IllegalStateException("CosId machine is not safe for generation: " + state);
        }
    }

    /** 健康检查只读取本地状态，不额外访问 Redis。 */
    public synchronized boolean isHealthy() {
        if (state == State.ACTIVE && System.nanoTime() - validUntilNanos >= 0) {
            invalidate("local deadline expired", null);
        }
        return state == State.ACTIVE;
    }

    private void guard() {
        long started = System.nanoTime();
        synchronized (this) {
            if (state == State.STOPPED || state == State.UNSAFE) {
                return;
            }
            if (state == State.ACTIVE && started - validUntilNanos >= 0) {
                invalidate("renewal started after deadline", null);
                return;
            }
        }
        try {
            // IO 不持有闸门；从请求开始计时，不能把网络耗时加进本地安全窗口。
            distributor.guard(namespace, instanceId, safeGuardDuration);
            synchronized (this) {
                if (state == State.STOPPED || state == State.UNSAFE) {
                    return;
                }
                long now = System.nanoTime();
                long nextDeadline = started + IdGeneratorConstants.LOCAL_VALID_DURATION.toNanos();
                if ((state == State.ACTIVE && now - validUntilNanos >= 0) || now - nextDeadline >= 0) {
                    invalidate("late renewal response", null);
                    return;
                }
                validUntilNanos = nextDeadline;
                state = State.ACTIVE;
            }
        } catch (Exception error) {
            synchronized (this) {
                invalidate("renewal failed; restart required", error);
            }
        }
    }

    private void invalidate(String reason, Exception error) {
        if (state != State.UNSAFE && state != State.STOPPED) {
            state = State.UNSAFE;
            log.error("CosId disabled namespace={} instance={} reason={}", namespace, instanceId, reason, error);
        }
    }

    /** 调用方应先与 generate 串行关闭，再归还机器号；迟到的续约不能重新开放闸门。 */
    public synchronized void stop() {
        state = State.STOPPED;
        scheduler.shutdownNow();
    }
}
