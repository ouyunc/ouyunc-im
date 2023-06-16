package com.ouyunc.im.lock;


import com.im.cache.l1.distributed.redis.redisson.RedissonFactory;
import com.ouyunc.im.constant.CacheConstant;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;


/**
 * @author fangzhenxun
 * @date 2020/1/10 13:54
 * @description redis 事务aop切面处理类
 * 这里使用redission来实现分布式锁
 */
@Aspect
public class RedissonDistributedLockAspect {
    private static Logger log = LoggerFactory.getLogger(RedissonDistributedLockAspect.class);



    /**
     * @param proceedingJoinPoint
     * @param distributedLock
     * @return java.lang.Object
     * @author fangzhenxun
     * @description 环绕通知，定义redis切面,不定义切点
     * 注意：由于aspectj maven编译器有bug 在调用方也织入了，所以 使用execution(* *(..)) 和 @annotation(distributedLock)两个配合使用
     * @date 2020/1/13 19:41
     **/
    @Around(value = "execution(* *(..)) && @annotation(distributedLock)")
    public Object aroundMethod(ProceedingJoinPoint proceedingJoinPoint, DistributedLock distributedLock) throws Throwable {
        //获得当前线程名称
        String currentThreadName = Thread.currentThread().getName();
        //获取redis锁的名称
        String lockName = distributedLock.lockName();
        //判断锁是否有值
        if (StringUtils.isBlank(lockName)) {
            //生成锁键名称 lock key name
            MethodSignature signature = (MethodSignature) proceedingJoinPoint.getSignature();
            lockName = CacheConstant.OUYUNC + CacheConstant.LOCK + signature.toLongString();
        }
        log.info("线程：{} 开始获取分布式锁,lockName: {}", currentThreadName, lockName);
        //获取锁
        RLock lock = RedissonFactory.INSTANCE.redissonClient().getLock(lockName);
        try {
            //获取锁的最长等待时间，以秒为单位
            long waitTime = distributedLock.waitTime();
            //锁的超时间(有效时间)
            long leaseTime = distributedLock.leaseTime();
            //如果没指定超时时间(包括等待超时时间和锁的超时时间)则直接上锁
            if (waitTime == 0 && leaseTime == 0) {
                //加锁 锁的有效期默认30秒
                lock.lock();
            }
            //如果指定了获取锁的等待超时间和锁的过期时间则尝试上锁
            if (waitTime != 0 && leaseTime != 0) {
                //如果没有获取锁的，抛出异常
                if (!lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS)) {
                    throw new RuntimeException(lockName + " 获取锁失败");
                }
            }
            //如果没有指定了获取锁的等待时间，但指定锁的有效时间
            if (waitTime == 0 && leaseTime != 0) {
                lock.lock(leaseTime, TimeUnit.SECONDS);
            }
            //如果指定了获取锁的等待时间，但没有指定锁的有效时间
            if (waitTime != 0 && leaseTime == 0) {
                lock.tryLock(waitTime, TimeUnit.SECONDS);
            }
            log.info("线程：{} 获取分布式锁 {} 完成,开始处理业务...", currentThreadName , lockName);
            //执行目标方法
            return proceedingJoinPoint.proceed();
        } catch (Exception e) {
            log.error("线程 {} 获取分布式锁 {} 失败！",currentThreadName, lockName);
            throw e;
        } finally {
            //释放锁
            log.info("线程：{} 正在释放分布式锁 {} ", currentThreadName , lockName);
            lock.unlock();
        }
    }

}
