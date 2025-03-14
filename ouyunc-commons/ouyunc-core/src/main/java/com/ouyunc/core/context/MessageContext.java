package com.ouyunc.core.context;


import com.ouyunc.cache.Cache;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.cache.distributed.redis.RedisDistributedCache;
import com.ouyunc.core.generator.IdGenerator;
import com.ouyunc.core.generator.SnowflakeIdGenerator;
import com.ouyunc.core.listener.MessageEventMulticaster;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.properties.MessageProperties;

/**
 * @Author fzx
 * @Description: Message 上下文
 **/
public class MessageContext {

    /**
     * message 事件多播器
     * */
    public static MessageEventMulticaster messageEventMulticaster;

    /**
     * message 基础消息属性配置类
     * */
    public static MessageProperties messageProperties;


    /**
     * 缓存
     */
    public static Cache<String, ?> cache = new RedisDistributedCache<>(CacheFactory.REDIS.instance());


    /**
     * 全局 id 生成器
     */
    private static IdGenerator<?> idGenerator = SnowflakeIdGenerator.INSTANCE;

    /**
     * 获取全局 id 生成器
     */
    @SuppressWarnings("unchecked")
    public static<T> IdGenerator<T> idGenerator () {
        return (IdGenerator<T>) idGenerator;
    }

    /**
     * 设置全局 id 生成器
     */
    public static<T> void setIdGenerator (IdGenerator<T> newIdGenerator) {
        idGenerator = newIdGenerator;
    }


    /**
     * @Author fzx
     * @Description 发布IM事件
     * @param event IMEvent事件的子类
     * @param async 是否异步发布事件 true-异步，false-同步
     */
    public static void publishEvent(MessageEvent event, boolean async) {
        if (messageEventMulticaster != null) {
            messageEventMulticaster.multicastEvent(event, async);
        }
    }

    /**
     * @Author fzx
     * @Description 同步发布IM事件
     * @param event IMEvent事件的子类
     */
    public static void publishEvent(MessageEvent event) {
        publishEvent(event, false);
    }
}
