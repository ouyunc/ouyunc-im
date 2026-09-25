package com.ouyunc.id;

import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.id.config.CosIdRedisConfiguration;
import com.ouyunc.id.config.IdGeneratorConstants;
import com.ouyunc.id.config.StrongClockSyncSnowflakeId;
import org.apache.commons.lang3.StringUtils;

/**
 * 雪花id生成器
 */
public enum CosIdSnowflakeIdGenerator implements IdGenerator{
    INSTANCE
    ;
    private static volatile me.ahoo.cosid.IdGenerator idGenerator;
    private static volatile CosIdRedisConfiguration configuration;
    private static volatile boolean closed;

    private static me.ahoo.cosid.IdGenerator snowflakeGenerator() {
        if (closed) {
            throw new IllegalStateException("CosId generator is closed");
        }
        me.ahoo.cosid.IdGenerator gen = idGenerator;
        if (gen != null) {
            return gen;
        }
        synchronized (CosIdSnowflakeIdGenerator.class) {
            if (closed) {
                throw new IllegalStateException("CosId generator is closed");
            }
            if (idGenerator == null) {
                CosIdRedisConfiguration cosIdRedisConfiguration =
                        new CosIdRedisConfiguration(CacheFactory.STRING_REDIS.instance(), resolveNamespace());
                configuration = cosIdRedisConfiguration;
                idGenerator = cosIdRedisConfiguration.getIdGeneratorProvider().getShare();
            }
            return idGenerator;
        }
    }

    /** 服务启动前调用，机器号分配失败不能进入接流状态。 */
    public void initialize() {
        snowflakeGenerator();
        if (!isHealthy()) {
            throw new IllegalStateException("CosId initialization is not healthy");
        }
    }

    /** 服务启动前分配机器号。关闭只走 {@link #shutdown()}，不注册 JVM 钩子。 */
    public void initializeManaged() {
        initialize();
    }

    /** 只读本地状态；未初始化和永久失效均不可就绪。 */
    public boolean isHealthy() {
        CosIdRedisConfiguration current = configuration;
        return !closed && current != null && current.isHealthy();
    }

    /** 幂等关闭，不允许关闭后隐式重新初始化。 */
    public synchronized void shutdown() {
        synchronized (CosIdSnowflakeIdGenerator.class) {
            closed = true;
            if (configuration != null) {
                configuration.shutdown();
            }
        }
    }

    /** 同一唯一性范围须共享分配记录；不同 namespace 并不会编码到最终 ID 中。 */
    private static String resolveNamespace() {
        String value = System.getProperty(IdGeneratorConstants.NAMESPACE_PROPERTY);
        if (value == null) {
            value = System.getenv(IdGeneratorConstants.NAMESPACE_ENV);
        }
        return value == null ? IdGeneratorConstants.DEFAULT_NAMESPACE : value.trim();
    }

    @Override
    public long generateId() {
        return snowflakeGenerator().generate();
    }

    @Override
    public String generateIdStr() {
        return String.valueOf(snowflakeGenerator().generate());
    }


    @Override
    public String generateId19Str() {
        return snowflakeGenerator().generateAsString();
    }

    @Override
    public String formatLongId19Str(String id) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("ID must not be blank; use explicit zero for a cursor boundary");
        }
        return formatLongId19Str(Long.parseLong(id));
    }

    @Override
    public String formatLongId19Str(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("ID must not be negative");
        }
        return StrongClockSyncSnowflakeId.ID_CONVERTER.asString(id);
    }

    @Override
    public long formatStrIdAsLong(String id) {
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("ID must not be blank");
        }
        long value = StrongClockSyncSnowflakeId.ID_CONVERTER.asLong(id);
        if (value < 0) {
            throw new IllegalArgumentException("ID must not be negative");
        }
        return value;
    }
}
