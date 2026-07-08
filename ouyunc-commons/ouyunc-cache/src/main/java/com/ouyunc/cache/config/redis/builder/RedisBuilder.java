package com.ouyunc.cache.config.redis.builder;


/**
 * @author fzx
 * @description redis 的创建者接口，包括领个抽象类
 */
public interface RedisBuilder<T> {



    /**
     * @author fzx
     * @description  redis 模版的实现构建类, 在该方法中涉及到策略模式的思想
     **/
    T build(int database);

}
