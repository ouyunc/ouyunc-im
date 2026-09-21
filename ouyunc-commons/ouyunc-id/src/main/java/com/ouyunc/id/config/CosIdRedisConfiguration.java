package com.ouyunc.id.config;

import me.ahoo.cosid.machine.*;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.spring.redis.SpringRedisMachineIdDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CosId Redis 分布式配置
 *
 * 功能：
 * 1. 自动分配机器号（用于 SnowflakeId）
 * 2. 机器号守护和续约
 * 3. 创建 SnowflakeId 生成器
 * 4. 应用关闭时自动清理资源
 */
public class CosIdRedisConfiguration {
    private static final Logger log = LoggerFactory.getLogger(CosIdRedisConfiguration.class);

    private final String namespace;
    private final SpringRedisMachineIdDistributor machineIdDistributor;
    private final MachineIdGuardian machineIdGuardian;
    private final IdGeneratorProvider idGeneratorProvider;
    private final MachineState machineState;
    private final InstanceId instanceId;
    private StrongClockSyncSnowflakeId guardedGenerator;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread shutdownHook = new Thread(this::shutdown, "CosId-Shutdown");

    /**
     * 构造函数
     *
     * @param redisTemplate Redis 模板
     * @param namespace 命名空间
     */
    public CosIdRedisConfiguration(StringRedisTemplate redisTemplate,
                                   String namespace) {
        this(redisTemplate, namespace, false);
    }

    /**
     * 构造函数（带稳定实例标识）
     *
     * @param redisTemplate Redis 模板
     * @param namespace 命名空间
     * @param stable 当前仅支持 false；稳定身份必须另行提供排他所有权协议
     */
    public CosIdRedisConfiguration(StringRedisTemplate redisTemplate,
                                   String namespace,
                                   boolean stable) {
        this.namespace = namespace;
        if (stable) {
            throw new IllegalArgumentException("Stable instance requires exclusive ownership; use process-unique mode");
        }
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("CosId namespace must not be blank");
        }

        // 1. 初始化机器号分发器
        MachineStateStorage stateStorage = new InMemoryMachineStateStorage();
        DefaultClockBackwardsSynchronizer clockSync = new DefaultClockBackwardsSynchronizer(
                IdGeneratorConstants.CLOCK_SPIN_THRESHOLD_MS, IdGeneratorConstants.CLOCK_BROKEN_THRESHOLD_MS);
        this.machineIdDistributor = new SpringRedisMachineIdDistributor(
                redisTemplate,
                stateStorage,
                clockSync
        );

        // 2. 分配机器号
        String instanceIdStr = getInstanceIdString();
        this.instanceId = InstanceId.of(instanceIdStr, stable);
        int machineBit = IdGeneratorConstants.MACHINE_BITS;
        Duration safeGuardDuration = IdGeneratorConstants.SAFE_GUARD_DURATION;

        this.machineState = machineIdDistributor.distribute(
                namespace,
                machineBit,
                instanceId,
                safeGuardDuration
        );
        log.info("MachineId distributed: {} for instance: {}", machineState, instanceId);

        // 3. 启动机器号守护线程
        long guardIntervalSeconds = IdGeneratorConstants.GUARD_INTERVAL_SECONDS;
        this.machineIdGuardian = new MachineIdGuardian(
                machineIdDistributor,
                namespace,
                instanceId,
                machineState,
                safeGuardDuration,
                guardIntervalSeconds
        );
        this.idGeneratorProvider = new DefaultIdGeneratorProvider();
        try {
            machineIdGuardian.start();
            initializeIdGenerators();
            // 同一个关闭入口先封闭发号，再停止守护和释放机器号。
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } catch (RuntimeException error) {
            shutdown();
            throw error;
        }

        log.info("CosId Redis configuration initialized successfully. Namespace: {}, MachineId: {}",
                namespace, machineState.getMachineId());
    }

    /**
     * 初始化 ID 生成器
     * 只创建 SnowflakeId
     */
    private void initializeIdGenerators() {
        // 创建 SnowflakeId
        MillisecondSnowflakeId snowflakeId = new NonBlockingSnowflakeId(machineState.getMachineId());
        guardedGenerator = new StrongClockSyncSnowflakeId(snowflakeId, machineIdGuardian);

        // 设置为共享的默认 ID 生成器
        idGeneratorProvider.setShare(guardedGenerator);

        // 也可以单独注册一个命名生成器（可选）
        idGeneratorProvider.set(IdGeneratorConstants.GENERATOR_NAME, guardedGenerator);
    }

    /**
     * 获取实例 ID 字符串
     * HOSTNAME 仅作诊断标签；随机启动标识保证同主机多个进程不会复用实例身份。
     */
    private String getInstanceIdString() {
        // 1. 尝试从环境变量获取
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isEmpty()) {
            return hostname + "-" + UUID.randomUUID();
        }

        // 2. 尝试从系统属性获取
        hostname = System.getProperty("hostname");
        if (hostname != null && !hostname.isEmpty()) {
            return hostname + "-" + UUID.randomUUID();
        }

        return "instance-" + UUID.randomUUID();
    }


    /**
     * 关闭资源
     * 先封闭发号，再停止守护线程并释放机器号；共享 Redis 资源不归本组件所有。
     */
    public void shutdown() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (guardedGenerator != null) {
            guardedGenerator.close();
        }
        log.info("Shutting down CosId Redis configuration for namespace: {}", namespace);

        // 1. 停止守护线程
        if (machineIdGuardian != null) {
            machineIdGuardian.stop();
        }

        // 回拨期间不能用较小的当前时间归还槽位，否则新持有者可能重走已发号时间段。
        // 不主动归还时保留 Redis 记录，由既有守护窗口回收；运维仍须保证节点时钟受控。
        if (guardedGenerator != null && System.currentTimeMillis() <= guardedGenerator.getLastTimestamp()) {
            log.warn("Skip CosId revert: clock has not advanced beyond last generated timestamp namespace={}", namespace);
            return;
        }

        // 2. 释放机器号
        try {
            if (machineIdDistributor != null && this.instanceId != null) {
                machineIdDistributor.revert(namespace, this.instanceId);
                log.info("MachineId reverted for instance: {}", this.instanceId);
            }
        } catch (Exception e) {
            log.error("Failed to revert machineId for instance: " + this.instanceId, e);
        }

        // RedisTemplate/连接工厂由缓存模块管理，本组件不获取临时连接来伪装资源释放。

        log.info("CosId Redis configuration shut down successfully");
    }

    // ========== Getter 方法 ==========

    /**
     * 获取 ID 生成器提供者
     */
    public IdGeneratorProvider getIdGeneratorProvider() {
        return idGeneratorProvider;
    }

    /** 无 Redis IO，供服务就绪检查使用。 */
    public boolean isHealthy() {
        return !closed.get() && machineIdGuardian.isHealthy();
    }

    /** 服务已有统一关闭钩子时移除独立钩子，保证退出通知完成后才关闭发号器。 */
    public void useManagedLifecycle() {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
    }

    /**
     * 获取机器号状态
     */
    public MachineState getMachineState() {
        return machineState;
    }

    /**
     * 获取实例 ID
     */
    public InstanceId getInstanceId() {
        return instanceId;
    }

    /**
     * 获取命名空间
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * 获取机器号分发器
     */
    public SpringRedisMachineIdDistributor getMachineIdDistributor() {
        return machineIdDistributor;
    }
}
