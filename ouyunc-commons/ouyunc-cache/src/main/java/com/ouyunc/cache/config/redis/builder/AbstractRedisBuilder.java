package com.ouyunc.cache.config.redis.builder;

import com.ouyunc.base.config.ConfigBinder;
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
     * 配置文件信息。由入口 {@link #bind()} 写入，子类通过 {@link #properties()} 读取。
     **/
    private static volatile RedisProperties redisProperties;
    /**
     * 获取当前选中的redis使用模式类型，如果没有设置primary则默认为单例模式类型
     **/
    private static volatile ModeEnum mode;

    /**
     * 获取所有redisson的模式策略
     **/
    private static volatile List<RedisStrategy> redisStrategyList;

    private static volatile boolean bound;

    /**
     * 由配置入口绑定 {@code ouyunc.cache.redis}。前缀缺失时记下空值，建连时再失败。
     */
    public static void bind() {
        synchronized (AbstractRedisBuilder.class) {
            redisProperties = YmlUtil.getActiveProfileValue(PropertiesConfigConstant.GLOBAL_CONFIG_FILE_LOCATION, PropertiesConfigConstant.CACHE_CONFIG_PROPERTIES_PREFIX, RedisProperties.class);
            if (redisProperties != null) {
                initModeAndStrategy();
            } else {
                mode = null;
                redisStrategyList = null;
            }
            bound = true;
        }
    }

    /**
     * YAML {@code ouyunc.cache.redis} 绑定结果，供 {@link com.ouyunc.cache.config.CacheFactory} 无参 instance() 读取默认 database。
     * 入口已执行且未配置该前缀时返回 null。
     */
    public static RedisProperties getRedisProperties() {
        return properties();
    }

    protected static RedisProperties properties() {
        ConfigBinder.requireBound(bound);
        return redisProperties;
    }

    protected static ModeEnum mode() {
        ConfigBinder.requireBound(bound);
        return mode;
    }

    protected static List<RedisStrategy> strategies() {
        ConfigBinder.requireBound(bound);
        return redisStrategyList == null ? List.of() : redisStrategyList;
    }

    public void setRedisProperties(RedisProperties redisProperties) {
        synchronized (AbstractRedisBuilder.class) {
            AbstractRedisBuilder.redisProperties = redisProperties;
            initModeAndStrategy();
            bound = true;
        }
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
