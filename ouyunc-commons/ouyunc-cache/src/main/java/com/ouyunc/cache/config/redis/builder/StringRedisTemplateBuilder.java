package com.ouyunc.cache.config.redis.builder;

import com.ouyunc.cache.config.constant.ModelEnum;
import com.ouyunc.cache.config.redis.strategy.RedisStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * @author fzx
 * @description 字符串redis模板
 */
public class StringRedisTemplateBuilder extends AbstractRedisBuilder<StringRedisTemplate> {

    private static final Logger logger = LoggerFactory.getLogger(StringRedisTemplateBuilder.class);

    /**
     * @author fzx
     * @description  redis 的字符串模板创建方法,
     **/
    @Override
    public StringRedisTemplate build(int database) {
        //1:读取配置文件,确定使用那种redis模式,并且根据配置的模式，来选出所使用的redis模式策略
        RedisStrategy redisStrategy = currentRedisStrategy();
        //这里使用lettuceConnectionFactory连接工厂
        RedisConnectionFactory lettuceConnectionFactory = redisStrategy.buildRedisConnectionFactory(database, redisProperties);
        StringRedisTemplate stringRedisTemplate = new StringRedisTemplate();
        //设置开启事务,会跟着数据库事务一起回滚（但是在该事务中不能获取set的值）
        //stringRedisTemplate.setEnableTransactionSupport(true);
        //设置redis连接工厂
        stringRedisTemplate.setConnectionFactory(lettuceConnectionFactory);
        //使用jackson序列化
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

    /**
     * @author fzx
     * @description  获得当前redis选中的配置策略
     **/
    private RedisStrategy currentRedisStrategy() {
        if (!redisStrategyList.isEmpty()) {
            return redisStrategyList.parallelStream().filter(redisStrategy -> {
                ModelEnum redisModel = redisStrategy.getModel();
                if (mode.equals(redisModel)) {
                    logger.info("当前StringRedisTemplate加载模式为========》" + redisStrategy.getModel().getRedisModel());
                }
                return mode.equals(redisModel);
            }).findAny().orElseThrow(() ->new RuntimeException("没有找到对应的配置方式!"));
        }
        logger.error("没有找到对应的配置方式!");
        throw new RuntimeException(  "没有找到对应的配置方式!");
    }
}
