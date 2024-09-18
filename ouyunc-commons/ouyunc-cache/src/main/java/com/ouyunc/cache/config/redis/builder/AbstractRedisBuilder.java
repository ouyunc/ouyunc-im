package com.ouyunc.cache.config.redis.builder;

import com.ouyunc.base.utils.YmlUtil;
import com.ouyunc.cache.config.constant.ModelEnum;
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
    protected static ModelEnum mode;

    /**
     * 获取所有redisson的模式策略
     **/
    protected static List<RedisStrategy> redisStrategyList;

    static {
        // 注意：如果想使用其他的配置文件名称，可以全局搜索 ouyunc-server.yml， 然后替换自己的文件名
        redisProperties = YmlUtil.getActiveProfileValue("ouyunc-server.yml", "ouyunc.cache.redis", RedisProperties.class);
        if (redisProperties != null) {
            initModeAndStrategy();
        }

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
            mode = ModelEnum.CLUSTER;
        }else if (redisProperties.getSentinel() != null && CollectionUtils.isNotEmpty(redisProperties.getSentinel().getNodes())) {
            mode = ModelEnum.SENTINEL;
        }else {
            mode = ModelEnum.STANDALONE;
        }
        redisStrategyList = new ArrayList<>() {{
            add(new StandaloneRedisStrategy());
            add(new SentinelRedisStrategy());
            add(new ClusterRedisStrategy());
        }};
    }
}
