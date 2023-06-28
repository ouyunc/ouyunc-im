package com.ouyunc.im.cache.l1.distributed.redis.lettuce.builder;


/**
 * @author fangzhenxun
 * @date 2020/1/8 15:18
 * @description redis 的创建者接口，包括领个抽象类
 */
public interface RedisBuilder<T> {


    /**
     * @author fangzhenxun
     * @description  redis 模版的实现构建类
     * 在该方法中涉及到策略模式的思想
     * @date  2020/1/8 13:45
     * @param
     * @return org.springframework.data.redis.core.RedisTemplate<java.lang.String,java.lang.Object>
     **/
    T build(int database);

}
