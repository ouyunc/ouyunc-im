package com.ouyunc.im.context;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.ouyunc.im.processor.AbstractChatbotMessageProcessor;
import com.ouyunc.im.processor.AbstractMessageProcessor;
import com.ouyunc.im.processor.content.AbstractMessageContentProcessor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Author fangzhenxun
 * @Description: IM处理器上下文
 **/
public class IMProcessContext {
    private static Logger log = LoggerFactory.getLogger(IMProcessContext.class);


    /**
     * 配置消息处理接口的所有实现类
     */
    public static LoadingCache<Byte, AbstractMessageProcessor> MESSAGE_PROCESSOR = Caffeine.newBuilder().build(new CacheLoader() {

        /**
         * 获取消息处理器的时候，先从缓存中取，如果没有则进行加载走load()方法
         */
        @Nullable
        @Override
        public Object load(@NonNull Object o) throws Exception {
            return null;
        }

        @Override
        public @NonNull Map loadAll(@NonNull Iterable keys) throws Exception {
            return null;
        }
    });


    /**
     * 配置消息内容处理接口的所有实现类
     */
    public static LoadingCache<Integer, AbstractMessageContentProcessor> MESSAGE_CONTENT_PROCESSOR = Caffeine.newBuilder().build(new CacheLoader() {

        /**
         * 获取消息处理器的时候，先从缓存中取，如果没有则进行加载走load()方法
         */
        @Nullable
        @Override
        public Object load(@NonNull Object o) throws Exception {
            return null;
        }

        @Override
        public @NonNull Map loadAll(@NonNull Iterable keys) throws Exception {
            return null;
        }
    });


    /**
     * 配置消息处理接口的所有实现类
     */
    public static List<AbstractChatbotMessageProcessor> CHAT_BOT_PROCESSOR = new ArrayList<>();

}
