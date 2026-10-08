package com.ouyunc.cache.config.redis.builder;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.ouyunc.cache.config.constant.ModeEnum;
import com.ouyunc.cache.config.redis.strategy.RedisStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author fzx
 * @description 相应式 ReactiveRedisTemplateBuilder 的构建类
 */
public class ReactiveRedisTemplateBuilder extends AbstractRedisBuilder<ReactiveRedisTemplate<?,?>> {

    private static final Logger logger = LoggerFactory.getLogger(ReactiveRedisTemplateBuilder.class);

    /**
     * 日期格式
     */
    public static String yyyy_MM_dd_HH_mm_ss ="yyyy-MM-dd HH:mm:ss";

    /**
     * @author fzx
     * @description  reactiveRedisTemplate 的实现构建类,这里使用单例模式来进行创建redis模板
     * 在该方法中涉及到策略模式的思想
     **/
    @Override
    public ReactiveRedisTemplate<?,?> build(int database) {
        //1:读取配置文件,确定使用那种redis模式,并且根据配置的模式，来选出所使用的redis模式策略
        RedisStrategy redisStrategy = currentRedisStrategy();
        //这里使用lettuceConnectionFactory连接工厂
        RedisConnectionFactory redisConnectionFactory = redisStrategy.buildRedisConnectionFactory(database,redisProperties);
        if (redisConnectionFactory instanceof LettuceConnectionFactory lettuceConnectionFactory) {
            //使用jackson序列化
            ObjectMapper objectMapper = new ObjectMapper();
            //针对于Date类型，文本格式化
            JavaTimeModule javaTimeModule = new JavaTimeModule();
            javaTimeModule.addSerializer(LocalDateTime.class,new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(yyyy_MM_dd_HH_mm_ss)));
            javaTimeModule.addDeserializer(LocalDateTime.class,new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(yyyy_MM_dd_HH_mm_ss)));
            objectMapper.registerModule(javaTimeModule);
            objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
            objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
            // 设置序列化
            GenericJackson2JsonRedisSerializer jackson2JsonRedisSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
            StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
            return new ReactiveRedisTemplate<>(lettuceConnectionFactory, RedisSerializationContext
                    .<String, Object>newSerializationContext()
                    .key(stringRedisSerializer)
                    .value(jackson2JsonRedisSerializer)
                    .hashKey(stringRedisSerializer)
                    .hashValue(jackson2JsonRedisSerializer)
                    .build());
        }else {
            logger.error("请使用LettuceConnectionFactory 来构建响应式ReactiveRedisTemplate!");
            throw new RuntimeException("请使用LettuceConnectionFactory 来构建响应式ReactiveRedisTemplate!");
        }
    }

    /**
     * @author fzx
     * @description  获得当前redis选中的配置策略
     **/
    private RedisStrategy currentRedisStrategy() {
        if (!redisStrategyList.isEmpty()) {
            return redisStrategyList.parallelStream().filter(redisStrategy -> {
                ModeEnum redisModel = redisStrategy.getModel();
                if (mode.equals(redisModel)) {
                    logger.info("当前reactiveRedisTemplate加载模式为========》" + redisStrategy.getModel().getRedisModel());
                }
                return mode.equals(redisModel);
            }).findAny().orElseThrow(() ->new RuntimeException("没有找到对应的配置方式!"));
        }
        logger.error("没有找到对应的配置方式,开始使用默认策略!");
        throw new RuntimeException("没有找到对应的配置方式!");
    }
}
