package com.ouyunc.im.cache.l1.distributed.redis.lettuce.strategy;

import com.ouyunc.im.cache.l1.distributed.redis.lettuce.enums.RedisEnum;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * @author fangzhenxun
 * @date 2020/1/8 15:01
 * @description redis 抽象策略类
 */
public interface RedisStrategy {

    /**
     * 标识redis实现类的模式类型
     *
     * @return
     */
    RedisEnum getType();


    /**
     * @author fangzhenxun
     * @description  获得不同策略模式的连接工厂
     * @date  2020/1/8 16:01
     * @return org.springframework.data.redis.connection.RedisConnectionFactory
     **/
    RedisConnectionFactory buildConnectionFactory(int database);

}
