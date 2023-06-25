package com.ouyunc.im.context;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.processor.AbstractMessageProcessor;
import com.ouyunc.im.processor.MessageProcessor;
import com.ouyunc.im.processor.content.AbstractMessageContentProcessor;
import com.ouyunc.im.processor.content.MessageContentProcessor;
import com.ouyunc.im.utils.ClassScanner;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;

/**
 * @Author fangzhenxun
 * @Description: IM处理器上下文
 **/
public class IMProcessContext {
    private static Logger log = LoggerFactory.getLogger(IMProcessContext.class);

    /**
     * 配置消息处理接口的所有实现类
     */
    public static LoadingCache<Byte, MessageProcessor> MESSAGE_PROCESSOR = Caffeine.newBuilder().build(new CacheLoader() {
        private Objenesis objenesis = new ObjenesisStd(true);

        /**
         * 获取消息处理器的时候，先从缓存中取，如果没有则进行加载走load()方法
         */
        @Nullable
        @Override
        public Object load(@NonNull Object o) throws Exception {
            // 消息枚举
            MessageEnum prototype = MessageEnum.prototype((byte) o);
            if (prototype != null) {
                Set<Class<?>> classes = ClassScanner.scanPackageBySuper(MessageProcessor.class.getPackage().getName(), MessageProcessor.class);
                for (Class<?> cls : classes) {
                    if (MessageProcessor.class.isAssignableFrom(cls)) {
                        // 排除自身以及抽象类
                        if (!MessageProcessor.class.equals(cls) && !Modifier.isAbstract(cls.getModifiers())) {
                            AbstractMessageProcessor messageProcessor = (AbstractMessageProcessor) objenesis.newInstance(cls);
                            if (prototype.equals(messageProcessor.messageType())) {
                                log.info("正在加载消息处理器 messageProcessor: {} ...", messageProcessor);
                                return messageProcessor;
                            }
                        }
                    }
                }
            }
            log.error("非法消息类型！");
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
    public static LoadingCache<Integer, MessageContentProcessor> MESSAGE_CONTENT_PROCESSOR = Caffeine.newBuilder().build(new CacheLoader() {
        private Objenesis objenesis = new ObjenesisStd(true);

        /**
         * 获取消息处理器的时候，先从缓存中取，如果没有则进行加载走load()方法
         */
        @Nullable
        @Override
        public Object load(@NonNull Object o) throws Exception {
            // 消息内容
            MessageContentEnum prototype = MessageContentEnum.prototype((int)o);
            if (prototype != null) {
                Set<Class<?>> classes = ClassScanner.scanPackageBySuper(MessageContentProcessor.class.getPackage().getName(), MessageContentProcessor.class);
                for (Class<?> cls : classes) {
                    if (MessageContentProcessor.class.isAssignableFrom(cls)) {
                        // 排除自身以及抽象类
                        if (!MessageContentProcessor.class.equals(cls) && !Modifier.isAbstract(cls.getModifiers())) {
                            AbstractMessageContentProcessor messageContentProcessor = (AbstractMessageContentProcessor) objenesis.newInstance(cls);
                            if (prototype.equals(messageContentProcessor.messageContentType())) {
                                log.info("正在加载消息内容处理器 messageContentProcessor: {} ...", messageContentProcessor);
                                return messageContentProcessor;
                            }
                        }
                    }
                }
            }
            log.error("非法消息内容类型！");
            return null;
        }

        @Override
        public @NonNull Map loadAll(@NonNull Iterable keys) throws Exception {
            return null;
        }
    });
}
