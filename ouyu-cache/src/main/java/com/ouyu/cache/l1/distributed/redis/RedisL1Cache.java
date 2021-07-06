package com.ouyu.cache.l1.distributed.redis;

import cn.hutool.core.lang.Singleton;
import com.ouyu.cache.constant.enums.RedisEnum;
import com.ouyu.cache.l1.distributed.AbstractDistributedL1Cache;
import com.ouyu.cache.l1.distributed.redis.lettuce.codec.RedisKeyValueCodec;
import com.ouyu.cache.l1.distributed.redis.lettuce.config.LettuceClusterConfiguration;
import com.ouyu.cache.l1.distributed.redis.lettuce.config.LettuceSentinelConfiguration;
import com.ouyu.cache.l1.distributed.redis.lettuce.config.LettuceStandaloneConfiguration;
import com.ouyu.cache.l1.distributed.redis.properties.RedisProperties;
import com.ouyu.cache.l1.distributed.redis.redisson.config.RedissonClusterConfiguration;
import com.ouyu.cache.l1.distributed.redis.redisson.config.RedissonSentinelConfiguration;
import com.ouyu.cache.l1.distributed.redis.redisson.config.RedissonStandaloneConfiguration;
import com.ouyu.cache.l1.distributed.redis.redisson.properties.RedissonClusterProperties;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.aeonbits.owner.ConfigFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * @Author fangzhenxun
 * @Description: redis 缓存的具体实现,需要做成单例
 * @Version V1.0
 **/
public class RedisL1Cache<K ,V > extends AbstractDistributedL1Cache<K,V> {
    private static Logger log = LoggerFactory.getLogger(RedisL1Cache.class);

    private volatile static RedisL1Cache redisL1Cache;

    private RedissonClient redissonClient;

    private GenericObjectPool<StatefulRedisConnection<K ,V>> pool;

    private static RedisProperties redisProperties = ConfigFactory.create(RedisProperties.class);


    private RedisL1Cache() {
        if (RedisEnum.STANDALONE.equals(redisProperties.primary())) {
            this.redissonClient = new RedissonStandaloneConfiguration().redissonClient();
            this.pool = new LettuceStandaloneConfiguration().genericObjectPool(new RedisKeyValueCodec<K, V> ());
        }else if (RedisEnum.SENTINEL.equals(redisProperties.primary())) {
            this.redissonClient = new RedissonSentinelConfiguration().redissonClient();
            this.pool = new LettuceSentinelConfiguration().genericObjectPool(new RedisKeyValueCodec<K, V> ());
        }else if (RedisEnum.CLUSTER.equals(redisProperties.primary())){
            this.redissonClient = new RedissonClusterConfiguration().redissonClient();
            this.pool = new LettuceClusterConfiguration().genericObjectPool(new RedisKeyValueCodec<K, V> ());
        }
    }


    public static<K,V> RedisL1Cache<K,V> getInstance() {
        //先判断对象是否已经实例过，没有实例化过才进⼊加锁代码
        if (redisL1Cache == null){
            //类对象加锁
            synchronized (RedisL1Cache.class) {
                if (redisL1Cache == null){
                    redisL1Cache = new RedisL1Cache<K,V>();
                }
            }
        }
        return redisL1Cache;
    }




    /**
     * 具体细节请看spring对redis封装的源码
     */
    abstract class Executor {
        /**
         * 定义/声明执行器
         */
        abstract V execute(RedisCommands<K, V> redisCommands);

        /**
         * 对execute 逻辑进行包装处理，相当于实现代理的功能
         */
        public V doExecute() {
            // 这里优雅的处理了连接的释放回连接池中
            try (final StatefulRedisConnection<K, V> statefulRedisConnection = pool.borrowObject()){
                final RedisCommands<K, V> redisCommands = statefulRedisConnection.sync();
                return execute(redisCommands);
            } catch (Exception e) {
                throw new RuntimeException("Redis execute exception", e);
            }
        }
    }

    public RLock getLock(String lockName) {
        return redissonClient.getLock(lockName);
    }



    @Override
    public void put(K key, V value) {
        new Executor(){
            @Override
            V execute(RedisCommands<K, V> redisCommands) {
                redisCommands.set(key, value);
                return null;
            }
        }.doExecute();
    }

    @Override
    public void put(K key, V value, long timeout, TimeUnit unit) {
        new Executor(){
            @Override
            V execute(RedisCommands<K, V> redisCommands) {
                redisCommands.setex(key, unit.toSeconds(timeout), value);
                return null;
            }
        }.doExecute();
    }

    @Override
    public void putIfAbsent(K key, V value) {
        new Executor(){
            @Override
            V execute(RedisCommands<K, V> redisCommands) {
                redisCommands.setnx(key, value);
                return null;
            }
        }.doExecute();
    }



    @Override
    public V get(K key) {
        return new Executor(){
            @Override
            V execute(RedisCommands<K, V> redisCommands) {
                return redisCommands.get(key);
            }
        }.doExecute();
    }


    @Override
    public void delete(K key) {
        new Executor(){
            @Override
            V execute(RedisCommands<K, V> redisCommands) {
                redisCommands.del(key);
                return null;
            }
        }.doExecute();
    }
}
