package com.ouyunc.id;

import com.ouyunc.base.utils.SnowflakeUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.id.config.CosIdRedisConfiguration;

/**
 * 雪花id生成器
 */
public enum CosIdSnowflakeIdGenerator implements IdGenerator{
    INSTANCE
    ;
    private static final me.ahoo.cosid.IdGenerator idGenerator;

    static {
        // 启动 CosId Redis 配置
        CosIdRedisConfiguration cosIdRedisConfiguration = new CosIdRedisConfiguration(CacheFactory.STRING_REDIS.instance(), "OUYUNC");
        idGenerator = cosIdRedisConfiguration.getIdGeneratorProvider().getShare();
    }
    @Override
    public long generateId() {
        return idGenerator.generate();
    }

    @Override
    public String generateIdStr() {
        return idGenerator.generateAsString();
    }

    @Override
    public String formatLong(long id) {
        return SnowflakeUtil.formatLong(id);
    }

    @Override
    public String formatLong(String id) {
        return SnowflakeUtil.formatLong(id);
    }
}
