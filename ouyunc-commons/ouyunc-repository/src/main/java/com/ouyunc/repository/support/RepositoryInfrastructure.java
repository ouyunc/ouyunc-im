package com.ouyunc.repository.support;

import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.db.jdbc.JdbcFactory;
import com.ouyunc.db.mongo.MongodbFactory;
import com.ouyunc.mq.core.MqFactory;
import com.ouyunc.mq.core.api.MqPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.concurrent.Executor;

/**
 * 仓库层共享基础设施（Redis / DB / MQ）。
 */
public final class RepositoryInfrastructure {

    public final MqPublisher mqPublisher;
    public final JdbcClient jdbcClient;
    public final MongoTemplate mongoTemplate;
    public final ReactiveMongoTemplate reactiveMongoTemplate;
    public final RedisTemplate redisTemplate;
    public final ReactiveStringRedisTemplate reactiveStringRedisTemplate;
    public final ReactiveRedisTemplate reactiveRedisTemplate;
    public final StringRedisTemplate stringRedisTemplate;
    public final RedisSerializer<String> stringSerializer;
    public final RedisSerializer<Object> valueSerializer;

    public RepositoryInfrastructure(MqPublisher mqPublisher,
                                    JdbcClient jdbcClient,
                                    MongoTemplate mongoTemplate,
                                    ReactiveMongoTemplate reactiveMongoTemplate,
                                    RedisTemplate redisTemplate,
                                    ReactiveStringRedisTemplate reactiveStringRedisTemplate,
                                    ReactiveRedisTemplate reactiveRedisTemplate,
                                    StringRedisTemplate stringRedisTemplate) {
        this.mqPublisher = mqPublisher;
        this.jdbcClient = jdbcClient;
        this.mongoTemplate = mongoTemplate;
        this.reactiveMongoTemplate = reactiveMongoTemplate;
        this.redisTemplate = redisTemplate;
        this.reactiveStringRedisTemplate = reactiveStringRedisTemplate;
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.stringSerializer = redisTemplate.getStringSerializer();
        this.valueSerializer = redisTemplate.getValueSerializer();
    }

    public static RepositoryInfrastructure createDefault() {
        RedisTemplate redisTemplate = CacheFactory.REDIS.instance();
        return new RepositoryInfrastructure(
                MqFactory.PUBLISHER.instance(),
                JdbcFactory.JDBC_CLIENT.instance(),
                MongodbFactory.MONGODB_TEMPLATE.instance(),
                MongodbFactory.REACTIVE_MONGODB_TEMPLATE.instance(),
                redisTemplate,
                CacheFactory.REACTIVE_STRING_REDIS.instance(),
                CacheFactory.REACTIVE_REDIS.instance(),
                CacheFactory.STRING_REDIS.instance()
        );
    }

    public Executor dbExecutor() {
        return ThreadPoolManager.repositoryExecutor();
    }
}
