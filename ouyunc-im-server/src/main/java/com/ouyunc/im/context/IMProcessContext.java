package com.ouyunc.im.context;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.ouyunc.im.processor.AbstractChatbotMessageProcessor;
import com.ouyunc.im.processor.AbstractMessageProcessor;
import com.ouyunc.im.processor.MessageProcessor;
import com.ouyunc.im.processor.content.AbstractMessageContentProcessor;
import com.ouyunc.im.utils.ClassScanner;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author fangzhenxun
 * @Description: IM处理器上下文
 **/
public class IMProcessContext {
    private static Logger log = LoggerFactory.getLogger(IMProcessContext.class);


    private static Objenesis objenesis = new ObjenesisStd(true);
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


    /**
     * 初始化处理器
     */
    static {
        Set<Class<?>> classes = new HashSet<>();
        try {
            // @todo 这里扫描包的返回可以放到配置文件中指定，后面进行优化处理
            classes = ClassScanner.scanPackageBySuper(IMServerContext.SERVER_CONFIG.getApplicationMainClass().getPackage().getName(), MessageProcessor.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (Class<?> cls : classes) {
            if (MessageProcessor.class.isAssignableFrom(cls)) {
                // 排除自身以及抽象类
                if (!MessageProcessor.class.equals(cls) && !Modifier.isAbstract(cls.getModifiers())) {
                    Object messageProcessor = objenesis.newInstance(cls);
                    if (AbstractMessageProcessor.class.isAssignableFrom(cls)) {
                        MESSAGE_PROCESSOR.put(((AbstractMessageProcessor)messageProcessor).messageType().getValue(), ((AbstractMessageProcessor)messageProcessor));
                    }
                    if (AbstractMessageContentProcessor.class.isAssignableFrom(cls)) {
                        MESSAGE_CONTENT_PROCESSOR.put(((AbstractMessageContentProcessor)messageProcessor).messageContentType().type(), ((AbstractMessageContentProcessor)messageProcessor));
                    }
                    if (AbstractChatbotMessageProcessor.class.isAssignableFrom(cls)) {
                        // 排除自身以及抽象类
                        CHAT_BOT_PROCESSOR.add((AbstractChatbotMessageProcessor)messageProcessor);
                    }
                }
            }
        }
        // spi
        ServiceLoader<MessageProcessor> messageProcessors = ServiceLoader.load(MessageProcessor.class);
        Iterator<MessageProcessor> iterator = messageProcessors.iterator();
        while (iterator.hasNext()) {
            MessageProcessor messageProcessor = iterator.next();
            Class<? extends MessageProcessor> cls = messageProcessor.getClass();
            if (AbstractMessageProcessor.class.isAssignableFrom(cls)) {
                MESSAGE_PROCESSOR.put(((AbstractMessageProcessor)messageProcessor).messageType().getValue(), ((AbstractMessageProcessor)messageProcessor));
            }
            if (AbstractMessageContentProcessor.class.isAssignableFrom(cls)) {
                MESSAGE_CONTENT_PROCESSOR.put(((AbstractMessageContentProcessor)messageProcessor).messageContentType().type(), ((AbstractMessageContentProcessor)messageProcessor));
            }
            if (AbstractChatbotMessageProcessor.class.isAssignableFrom(cls)) {
                // 排除自身以及抽象类
                CHAT_BOT_PROCESSOR.add((AbstractChatbotMessageProcessor)messageProcessor);
            }
        }
        // 排序并整合
        CHAT_BOT_PROCESSOR = CHAT_BOT_PROCESSOR.stream().sorted(Comparator.comparingInt(AbstractChatbotMessageProcessor::order)).collect(Collectors.toList());
        for (int i = 0; i < CHAT_BOT_PROCESSOR.size(); i++) {
            if(i == CHAT_BOT_PROCESSOR.size() -1){
                CHAT_BOT_PROCESSOR.get(i).setNextHandler(null);
            } else {
                CHAT_BOT_PROCESSOR.get(i).setNextHandler(CHAT_BOT_PROCESSOR.get(i + 1));
            }
        }
    }
}
