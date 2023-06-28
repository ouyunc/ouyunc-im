package com.ouyunc.im.cache.l1.distributed.redis.redisson.builder;

/**
 * @author fangzhenxun
 * @date 2020/1/10 15:04
 * @description redisson 建造抽象类
 */
public interface RedissonBuilder<T> {


    /**
     * @author fangzhenxun
     * @description  redisson 的建方法
     * @date  2020/1/10 15:07
     * @param
     * @return T
     **/
    T build(int database);
}
