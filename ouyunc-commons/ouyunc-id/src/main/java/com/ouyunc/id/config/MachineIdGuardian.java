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
 * 机器号守护线程
 * 定期续约机器号，防止过期被回收
 */
public class MachineIdGuardian {
    private static final Logger log = LoggerFactory.getLogger(MachineIdGuardian.class);

    private final MachineIdDistributor machineIdDistributor;
    private final String namespace;
    private final InstanceId instanceId;
    private final MachineState machineState;
    private final Duration safeGuardDuration;
    private final ScheduledExecutorService scheduler;
    private final long guardIntervalSeconds;

    private volatile boolean running = false;

    public MachineIdGuardian(MachineIdDistributor machineIdDistributor,
                             String namespace,
                             InstanceId instanceId,
                             MachineState machineState,
                             Duration safeGuardDuration,
                             long guardIntervalSeconds) {
        this.machineIdDistributor = machineIdDistributor;
        this.namespace = namespace;
        this.instanceId = instanceId;
        this.machineState = machineState;
        this.safeGuardDuration = safeGuardDuration;
        this.guardIntervalSeconds = guardIntervalSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MachineIdGuardian-" + instanceId.getInstanceId());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动守护线程
     */
    public void start() {
        if (running) {
            log.warn("MachineIdGuardian is already running for instance: {}", instanceId);
            return;
        }

        running = true;
        // 立即执行一次
        guard();

        // 定期执行
        scheduler.scheduleAtFixedRate(
                this::guard,
                guardIntervalSeconds,
                guardIntervalSeconds,
                TimeUnit.SECONDS
        );

        log.info("MachineIdGuardian started for instance: {}, guard interval: {}s",
                instanceId, guardIntervalSeconds);
    }

    /**
     * 停止守护线程
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("MachineIdGuardian stopped for instance: {}", instanceId);
    }

    /**
     * 执行守护操作（续约）
     *
     * guard 方法会从 MachineStateStorage 中获取 MachineState，
     * 并自动更新时间戳，所以不需要手动传入 MachineState
     */
    private void guard() {
        if (!running) {
            return;
        }

        try {
            // guard 方法签名：guard(String namespace, InstanceId instanceId, Duration safeGuardDuration)
            // 它会自动从 MachineStateStorage 获取 MachineState 并更新时间戳
            machineIdDistributor.guard(namespace, instanceId, safeGuardDuration);

            if (log.isDebugEnabled()) {
                log.debug("MachineId guarded successfully for instance: {}, machineId: {}",
                        instanceId, machineState.getMachineId());
            }
        } catch (Exception e) {
            log.error("Failed to guard machineId for instance: " + instanceId, e);
        }
    }

    /**
     * 注册 JVM 关闭钩子，自动释放机器号
     */
    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down MachineIdGuardian for instance: {}", instanceId);
            stop();
            try {
                machineIdDistributor.revert(namespace, instanceId);
                log.info("MachineId reverted for instance: {}", instanceId);
            } catch (Exception e) {
                log.error("Failed to revert machineId for instance: " + instanceId, e);
            }
        }, "MachineIdGuardian-ShutdownHook-" + instanceId.getInstanceId()));
    }
}

