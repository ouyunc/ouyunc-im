package com.ouyu.cache.l1.distributed.redis.redisson.lock;

import java.lang.annotation.*;

/**
 * @author fangzhenxun
 * @description redis 事务注解，使用在方法级别上,作为分布式锁使用
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface DistributedLock {


    /**
     * 获取锁的最长等待时间，以秒为单位
     * 如果不指定，则会一直等待
     */
    long waitTime() default 0;


    /**
     * 锁的超时时间(锁有效时间)，以秒为单位
     * 如果不指定，则不会超时，只能手工解锁时才会释放
     */
    long leaseTime() default 0 ;


    /**
     * 锁名字，如果不指定，则使用当前方法的全路径名
     */
    String lockName() default "";
}
