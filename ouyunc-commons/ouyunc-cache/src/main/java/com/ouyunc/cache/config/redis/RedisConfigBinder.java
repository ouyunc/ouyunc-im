package com.ouyunc.cache.config.redis;

import com.ouyunc.base.config.ConfigBinder;
import com.ouyunc.cache.config.redis.builder.AbstractRedisBuilder;

/**
 * 把 Redis 配置挂到启动入口，构建器只读取绑定结果。
 */
public final class RedisConfigBinder implements ConfigBinder {

    @Override
    public void bind() {
        AbstractRedisBuilder.bind();
    }
}
