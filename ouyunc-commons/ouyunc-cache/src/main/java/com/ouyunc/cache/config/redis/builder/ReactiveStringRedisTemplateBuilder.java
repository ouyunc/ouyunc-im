package com.ouyunc.cache.config.redis.builder;

import com.ouyunc.cache.config.constant.ModeEnum;
import com.ouyunc.cache.config.redis.strategy.RedisStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * @author fzx
 * @description 相应式 ReactiveStringRedisTemplateBuilder 的构建类
 */
public class ReactiveStringRedisTemplateBuilder extends AbstractRedisBuilder<ReactiveStringRedisTemplate> {

    private static final Logger logger = LoggerFactory.getLogger(ReactiveStringRedisTemplateBuilder.class);

    /**
     * 日期格式
     */
    public static String yyyy_MM_dd_HH_mm_ss ="yyyy-MM-dd HH:mm:ss";

    /**
     * @author fzx
     * @description  reactiveStringRedisTemplate 的实现构建类,这里使用单例模式来进行创建redis模板
     * 在该方法中涉及到策略模式的思想
     **/
    @Override
    public ReactiveStringRedisTemplate build(int database) {
        //1:读取配置文件,确定使用那种redis模式,并且根据配置的模式，来选出所使用的redis模式策略
        RedisStrategy redisStrategy = currentRedisStrategy();
        //这里使用lettuceConnectionFactory连接工厂
        RedisConnectionFactory redisConnectionFactory = redisStrategy.buildRedisConnectionFactory(database,redisProperties);
        if (redisConnectionFactory instanceof LettuceConnectionFactory lettuceConnectionFactory) {
            // 设置序列化
            StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
            return new ReactiveStringRedisTemplate(lettuceConnectionFactory, RedisSerializationContext
                    .<String, String>newSerializationContext()
                    .key(stringRedisSerializer)
                    .value(stringRedisSerializer)
                    .hashKey(stringRedisSerializer)
                    .hashValue(stringRedisSerializer)
                    .build());
        }else {
            logger.error("请使用LettuceConnectionFactory 来构建响应式ReactiveStringRedisTemplate!");
            throw new RuntimeException("请使用LettuceConnectionFactory 来构建响应式ReactiveStringRedisTemplate!");
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
                    logger.info("当前reactiveStringRedisTemplate加载模式为========》" + redisStrategy.getModel().getRedisModel());
                }
                return mode.equals(redisModel);
            }).findAny().orElseThrow(() ->new RuntimeException("没有找到对应的配置方式!"));
        }
        logger.error("没有找到对应的配置方式,开始使用默认策略!");
        throw new RuntimeException("没有找到对应的配置方式!");
    }
}
