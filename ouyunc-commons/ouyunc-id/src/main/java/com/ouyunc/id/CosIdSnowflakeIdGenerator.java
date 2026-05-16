package com.ouyunc.id;

import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.id.config.CosIdRedisConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 雪花id生成器
 */
public enum CosIdSnowflakeIdGenerator implements IdGenerator{
    INSTANCE
    ;
    private static final me.ahoo.cosid.IdGenerator idGenerator;
    private static final Logger log = LoggerFactory.getLogger(CosIdSnowflakeIdGenerator.class);

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
        return String.valueOf(idGenerator.generate());
    }


    @Override
    public String generateId19Str() {
        return idGenerator.generateAsString();
    }

    @Override
    public String formatLongId19Str(String id) {
        if (StringUtils.isBlank(id)) {
            log.error("格式化ID失败，ID为空！");
            return formatLongId19Str(NumberConstant.NUMBER_0);
        }
        return formatLongId19Str(Long.parseLong(id));
    }

    @Override
    public String formatLongId19Str(long id) {
        return idGenerator.idConverter().asString(id);
    }

    @Override
    public long formatStrIdAsLong(String id) {
        return idGenerator.idConverter().asLong(id);
    }
}
