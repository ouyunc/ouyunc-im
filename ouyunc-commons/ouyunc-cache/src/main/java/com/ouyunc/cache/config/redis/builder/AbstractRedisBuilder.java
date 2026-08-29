package com.ouyunc.cache.config.redis.builder;

import com.ouyunc.base.constant.PropertiesConfigConstant;
import com.ouyunc.base.utils.YmlUtil;
import com.ouyunc.cache.config.constant.ModeEnum;
import com.ouyunc.cache.config.redis.properties.RedisProperties;
import com.ouyunc.cache.config.redis.strategy.ClusterRedisStrategy;
import com.ouyunc.cache.config.redis.strategy.RedisStrategy;
import com.ouyunc.cache.config.redis.strategy.SentinelRedisStrategy;
import com.ouyunc.cache.config.redis.strategy.StandaloneRedisStrategy;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽象redis 构建这
 */
public abstract class AbstractRedisBuilder<T> implements RedisBuilder<T>{

    /**
     * 配置文件信息
     **/
    protected static RedisProperties redisProperties;
    /**
     * 获取当前选中的redis使用模式类型，如果没有设置primary则默认为单例模式类型
     **/
    protected static ModeEnum mode;

    /**
     * 获取所有redisson的模式策略
     **/
    protected static List<RedisStrategy> redisStrategyList;

    static {
        // 注意：如果想使用其他的配置文件名称，可以全局搜索 ouyunc-server.yml， 然后替换自己的文件名
        redisProperties = YmlUtil.getActiveProfileValue(PropertiesConfigConstant.GLOBAL_CONFIG_FILE_LOCATION, PropertiesConfigConstant.CACHE_CONFIG_PROPERTIES_PREFIX, RedisProperties.class);
        if (redisProperties != null) {
            initModeAndStrategy();
        }

    }

    /**
     * YAML {@code ouyunc.cache.redis} 绑定结果，供 {@link com.ouyunc.cache.config.CacheFactory} 无参 instance() 读取默认 database。
     * 类加载时已从配置文件初始化，可能为 null（无配置文件时）。
     */
    public static RedisProperties getRedisProperties() {
        return redisProperties;
    }

    public void setRedisProperties(RedisProperties redisProperties) {
        AbstractRedisBuilder.redisProperties = redisProperties;
        initModeAndStrategy();
    }

    /**
     * 初始化mode和策略
     */
    public static void initModeAndStrategy() {
        // 从配置中心读取配置信息,请注意类的初始化和加载顺序
        if (redisProperties.getCluster() != null && CollectionUtils.isNotEmpty(redisProperties.getCluster().getNodes())) {
            mode = ModeEnum.CLUSTER;
        }else if (redisProperties.getSentinel() != null && CollectionUtils.isNotEmpty(redisProperties.getSentinel().getNodes())) {
            mode = ModeEnum.SENTINEL;
        }else {
            mode = ModeEnum.STANDALONE;
        }
        redisStrategyList = new ArrayList<>() {{
            add(new StandaloneRedisStrategy());
            add(new SentinelRedisStrategy());
            add(new ClusterRedisStrategy());
        }};
    }
}
