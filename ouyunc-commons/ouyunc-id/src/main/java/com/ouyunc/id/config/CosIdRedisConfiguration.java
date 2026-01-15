package com.ouyunc.id.config;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.machine.DefaultClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.LocalMachineStateStorage;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.snowflake.ClockSyncSnowflakeId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.spring.redis.SpringRedisMachineIdDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

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

    private final StringRedisTemplate redisTemplate;
    private final String namespace;
    private final SpringRedisMachineIdDistributor machineIdDistributor;
    private final MachineIdGuardian machineIdGuardian;
    private final IdGeneratorProvider idGeneratorProvider;
    private final MachineState machineState;
    private final InstanceId instanceId;

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
     * @param stable 是否为稳定实例（如 Kubernetes StatefulSet），稳定实例的机器号不会被真正释放
     */
    public CosIdRedisConfiguration(StringRedisTemplate redisTemplate,
                                   String namespace,
                                   boolean stable) {
        this.redisTemplate = redisTemplate;
        this.namespace = namespace;

        // 1. 初始化机器号分发器
        LocalMachineStateStorage stateStorage = new LocalMachineStateStorage("./cosid-machine-state/");
        DefaultClockBackwardsSynchronizer clockSync = new DefaultClockBackwardsSynchronizer(10, 2000);
        this.machineIdDistributor = new SpringRedisMachineIdDistributor(
                redisTemplate,
                stateStorage,
                clockSync
        );

        // 2. 分配机器号
        String instanceIdStr = getInstanceIdString();
        this.instanceId = InstanceId.of(instanceIdStr, stable);
        int machineBit = 10; // 10 位机器号，支持 1024 台机器
        Duration safeGuardDuration = Duration.ofMinutes(5); // 5 分钟安全守护时间

        this.machineState = machineIdDistributor.distribute(
                namespace,
                machineBit,
                instanceId,
                safeGuardDuration
        );
        log.info("MachineId distributed: {} for instance: {}", machineState, instanceId);

        // 3. 启动机器号守护线程
        long guardIntervalSeconds = safeGuardDuration.getSeconds() / 3; // 守护间隔为安全时间的 1/3
        this.machineIdGuardian = new MachineIdGuardian(
                machineIdDistributor,
                namespace,
                instanceId,
                machineState,
                safeGuardDuration,
                guardIntervalSeconds
        );
        machineIdGuardian.start();
        machineIdGuardian.registerShutdownHook(); // 注册关闭钩子

        // 4. 创建 ID 生成器
        this.idGeneratorProvider = new DefaultIdGeneratorProvider();
        initializeIdGenerators();

        log.info("CosId Redis configuration initialized successfully. Namespace: {}, MachineId: {}",
                namespace, machineState.getMachineId());
    }

    /**
     * 初始化 ID 生成器
     * 只创建 SnowflakeId
     */
    private void initializeIdGenerators() {
        // 创建 SnowflakeId
        MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(
                CosId.COSID_EPOCH,
                41,  // 时间戳位数
                10,  // 机器号位数
                12,  // 序列号位数
                machineState.getMachineId()
        );
        ClockSyncSnowflakeId clockSyncSnowflakeId = new ClockSyncSnowflakeId(
                snowflakeId,
                new DefaultClockBackwardsSynchronizer(10, 2000)
        );

        // 设置为共享的默认 ID 生成器
        idGeneratorProvider.setShare(clockSyncSnowflakeId);

        // 也可以单独注册一个命名生成器（可选）
        idGeneratorProvider.set("snowflake", clockSyncSnowflakeId);
    }

    /**
     * 获取实例 ID 字符串
     * 优先级：HOSTNAME 环境变量 > 系统属性 > 时间戳
     */
    private String getInstanceIdString() {
        // 1. 尝试从环境变量获取
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isEmpty()) {
            return hostname;
        }

        // 2. 尝试从系统属性获取
        hostname = System.getProperty("hostname");
        if (hostname != null && !hostname.isEmpty()) {
            return hostname;
        }

        // 3. 使用时间戳作为后备方案
        return "instance-" + System.currentTimeMillis();
    }


    /**
     * 关闭资源
     * 停止守护线程，释放机器号，关闭 Redis 连接
     */
    public void shutdown() {
        log.info("Shutting down CosId Redis configuration for namespace: {}", namespace);

        // 1. 停止守护线程
        if (machineIdGuardian != null) {
            machineIdGuardian.stop();
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

        // 3. 关闭 Redis 连接
        if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
            try {
                RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
                if (connectionFactory != null) {
                    connectionFactory.getConnection().close();
                    log.info("Redis connection closed");
                }
            } catch (Exception e) {
                log.error("Failed to close Redis connection", e);
            }
        }

        log.info("CosId Redis configuration shut down successfully");
    }

    // ========== Getter 方法 ==========

    /**
     * 获取 ID 生成器提供者
     */
    public IdGeneratorProvider getIdGeneratorProvider() {
        return idGeneratorProvider;
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

