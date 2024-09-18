package com.ouyunc.core.context;


import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ouyunc.base.constant.enums.DeviceType;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.cache.Cache;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.cache.distributed.redis.RedisDistributedCache;
import com.ouyunc.cache.local.caffeine.CaffeineLocalCache;
import com.ouyunc.core.generator.IdGenerator;
import com.ouyunc.core.generator.SnowflakeIdGenerator;
import com.ouyunc.core.listener.MessageEventMulticaster;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.properties.MessageProperties;
import io.netty.channel.ChannelHandlerContext;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.redisson.api.RedissonClient;

import java.util.Map;
import java.util.Set;

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
}
