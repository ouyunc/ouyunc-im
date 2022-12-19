package com.im.cache.l1.distributed.redis.lettuce.builder.impl;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.im.cache.l1.distributed.redis.lettuce.builder.RedisBuilder;
import com.im.cache.l1.distributed.redis.lettuce.properties.RedisPrimaryProperties;
import com.im.cache.l1.distributed.redis.lettuce.strategy.RedisStrategy;
import com.im.cache.l1.distributed.redis.lettuce.enums.RedisEnum;
import com.im.cache.l1.distributed.redis.lettuce.strategy.impl.ClusterRedisStrategy;
import com.im.cache.l1.distributed.redis.lettuce.strategy.impl.SentinelRedisStrategy;
import com.im.cache.l1.distributed.redis.lettuce.strategy.impl.StandaloneRedisStrategy;
import org.aeonbits.owner.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author fangzhenxun
 * @date 2020/1/8 13:48
 * @description 字符串redis模板
 */
public class StringRedisTemplateBuilder implements RedisBuilder<StringRedisTemplate> {

    private static Logger logger = LoggerFactory.getLogger(StringRedisTemplateBuilder.class);


    /**
     * 获取当前选中的redis使用模式类型，如果没有设置primary则默认为单例模式类型
     **/
    private static final RedisEnum type;


    /**
     * 获取所有redis的模式策略
     **/
    private static final List<RedisStrategy> redisStrategyList;

    /**
     * 构建锁
     */
    private static final Lock LOCK;

    /**
     * 初始化数据
     **/
    static {
        type = ConfigFactory.create(RedisPrimaryProperties.class).primary();
        redisStrategyList = new ArrayList<>();
        redisStrategyList.add(new StandaloneRedisStrategy());
        redisStrategyList.add(new SentinelRedisStrategy());
        redisStrategyList.add(new ClusterRedisStrategy());
        LOCK = new ReentrantLock();
    }

    /**
     * @author fangzhenxun
     * @description  redis 的字符串模板创建方法,
     * @date  2020/1/8 14:08
     * @param
     * @return org.springframework.data.redis.core.StringRedisTemplate
     **/
    @Override
    public StringRedisTemplate build(int database) {
        StringRedisTemplate stringRedisTemplate = null;
        try {
            LOCK.tryLock(10, TimeUnit.MILLISECONDS);
            if (null == stringRedisTemplate) {
                //1:读取配置文件,确定使用那种redis模式,并且根据配置的模式，来选出所使用的redis模式策略
                RedisStrategy redisStrategy = currentRedisStrategy();
                //这里使用lettuceConnectionFactory连接工厂
                RedisConnectionFactory lettuceConnectionFactory = redisStrategy.buildConnectionFactory(database);
                stringRedisTemplate = new StringRedisTemplate();
                //设置开启事务,会跟着数据库事务一起回滚（但是在该事务中不能获取set的值）
                stringRedisTemplate.setEnableTransactionSupport(true);
                //设置redis连接工厂
                stringRedisTemplate.setConnectionFactory(lettuceConnectionFactory);
                //使用jackson序列化
                Jackson2JsonRedisSerializer jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer(Object.class);
                ObjectMapper objectMapper = new ObjectMapper();
                JavaTimeModule javaTimeModule = new JavaTimeModule();
                javaTimeModule.addSerializer(LocalDateTime.class,new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                javaTimeModule.addDeserializer(LocalDateTime.class,new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                objectMapper.registerModule(javaTimeModule);
                objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
                objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
                objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
                jackson2JsonRedisSerializer.setObjectMapper(objectMapper);
                StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
                //key和hashKey使用String序列化
                stringRedisTemplate.setKeySerializer(stringRedisSerializer);
                stringRedisTemplate.setHashKeySerializer(stringRedisSerializer);
                //value和hashValue使用jackson的字符串序列化
                stringRedisTemplate.setValueSerializer(new StringRedisSerializer());
                stringRedisTemplate.setHashValueSerializer(new StringRedisSerializer());
                //模板初始化，不设置可能会抛出异常
                stringRedisTemplate.afterPropertiesSet();
                return stringRedisTemplate;
            }
        } catch (Exception e) {
            logger.error("redis 配置模版失败->{}" ,e.getMessage());
        } finally {
            //释放锁
            LOCK.unlock();
        }
        return stringRedisTemplate;
    }

    /**
     * @author fangzhenxun
     * @description  获得当前redis选中的配置策略
     * @date  2020/1/8 15:54
     * @param
     * @return com.xyt.cache.config.redis.lettuce.strategy.RedisStrategy
     **/
    private RedisStrategy currentRedisStrategy() {
        if (!redisStrategyList.isEmpty()) {
            return redisStrategyList.parallelStream().filter(redisStrategy -> {
                String redisModel = redisStrategy.getType().name();
                if (type.equals(redisModel)) {
                    logger.info("当前StringRedisTemplate加载模式为========》" + redisStrategy.getType().getRedisModel());
                }
                return type.equals(redisModel);
            }).findAny().orElseThrow(() ->new RuntimeException("没有找到对应的配置方式"));
        }
        logger.error("没有找到对应的配置方式");
        throw new RuntimeException(  "没有找到对应的配置方式");
    }
}
