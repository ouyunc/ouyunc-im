package com.ouyunc.im.cache.l1.distributed.redis.redisson.strategy;

import com.ouyunc.im.cache.l1.distributed.redis.redisson.enums.RedisEnum;
import org.redisson.config.Config;

/**
 * @author fangzhenxun
 * @date 2020/1/10 17:01
 * @description redisson 的抽象策略
 */
public interface RedissonStrategy {

    /**
     * 标识redisson实现类的模式类型
     *
     * @return
     */
    RedisEnum getType();

    /**
     * @author fangzhenxun
     * @description  构建redission的配置类
     * @date  2020/1/10 17:08
     * @param
     * @return org.redisson.config.Config
     **/
    Config buildConfig(int database);
}
