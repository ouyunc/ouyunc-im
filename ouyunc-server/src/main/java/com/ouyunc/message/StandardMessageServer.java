package com.ouyunc.message;

import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.MessageContentType;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.executor.ThreadPoolConfig;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.MessageProtocol;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.*;
import com.ouyunc.core.engine.LoadPropertiesEngine;
import com.ouyunc.core.intercept.AbstractMessageInterceptor;
import com.ouyunc.core.intercept.Interceptor;
import com.ouyunc.core.listener.DisruptorMessageEventMulticaster;
import com.ouyunc.core.listener.MessageEventMulticaster;
import com.ouyunc.core.listener.MessageEventListener;
import com.ouyunc.core.listener.EventListener;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.processor.BiProcessor;
import com.ouyunc.core.properties.CommandLineArgs;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.dispatcher.ProtocolDispatcherBiProcessor;
import com.ouyunc.message.monitor.MonitorInitializer;
import com.ouyunc.message.processor.*;
import com.ouyunc.message.properties.MessageServerProperties;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author fzx
 * @Description: 标准MessageServer 抽象实现类
 **/
public class StandardMessageServer extends AbstractMessageServer {
    private static final Logger log = LoggerFactory.getLogger(StandardMessageServer.class);


    /***
     * @author fzx
     * @description 加载属性配置文件, 属性加载顺序优先级：args>system.properties>yml
     */
    @Override
    void loadProperties(String... args) {
        log.debug("正在加载配置信息......");
        // 1,加载yml以及system.properties配置
        // 2,加载args
        LoadPropertiesEngine loader = new LoadPropertiesEngine();
        MessageServerContext.messageProperties =  loadArgsProperties(loader.loadProperties(MessageServerProperties.class, System.getProperties()), resolverArgs(args));
        // 设置本地localhost
        MessageServerContext.serverProperties().setLocalHost(IpUtil.getLocalHost());
        log.debug("配置信息加载完成：{}", MessageServerContext.serverProperties().toString());
    }

    @Override
    void afterPropertiesSet() {
        super.afterPropertiesSet();
        log.debug("开始初始化线程池配置...");
        @SuppressWarnings("unchecked")
        Map<String, Object> threadPoolSection = YmlUtil.getValue("ouyunc-server.yml", "ouyunc.message.thread-pool", Map.class);
        ThreadPoolConfig config = ThreadPoolConfig.fromYaml(threadPoolSection);
        ThreadPoolManager.initialise(config);
        log.debug("线程池配置初始化完成");
        
        // 初始化资源监控（注册缓存实例）
        MonitorInitializer.initialize();
    }


    /***
     * @author fzx
     * @description 加载事件监听器
     */
    @SuppressWarnings("unchecked")
    @Override
    void loadEventListener() {
        log.debug("正在加载事件监听器......");
        Set<Class<?>> messageListenerClazzSet = new HashSet<>();
        try {
            for (String messageListenersScanPackagePath : MessageServerContext.serverProperties().getMessageListenersScanPackagePaths()) {
                messageListenerClazzSet.addAll(ClassScannerUtil.scanPackageBySuper(messageListenersScanPackagePath, MessageEventListener.class));
            }
        } catch (IOException e) {
            log.error("扫描事件监听器失败: {}", e.getMessage());
        }
        // 排除不是直接实现该接口的（DisruptorMessageEventMulticaster + event-listener 线程池）
        MessageEventMulticaster messageEventMulticaster =
                new DisruptorMessageEventMulticaster(ThreadPoolManager.eventListenerExecutor());
        log.debug("事件多播器: DisruptorMessageEventMulticaster");
        // 扫描结果为 HashSet 无序；按 @Order（值小优先）再按类名排序后注册，保证同事件类型多 listener 调用顺序稳定
        List<Class<?>> orderedListenerClasses = new ArrayList<>(messageListenerClazzSet);
        orderedListenerClasses.sort(Comparator.comparingInt(StandardMessageServer::messageListenerOrder).thenComparing(Class::getName));
        for (Class<?> messageListenerClazz : orderedListenerClasses) {
            if (MessageEventListener.class.isAssignableFrom(messageListenerClazz)) {
                // 排除自身以及抽象类
                if (!MessageEventListener.class.equals(messageListenerClazz) && !Modifier.isAbstract(messageListenerClazz.getModifiers())) {
                    MessageEventListener<MessageEvent> messageListener = (MessageEventListener<MessageEvent>) objenesis.newInstance(messageListenerClazz);
                    messageEventMulticaster.addMessageListener(messageListener);
                    log.debug("事件监听器： {}", messageListener);
                }
            } else {
                log.error("{} 不是MessageEventListener 的实现类", messageListenerClazz.getName());
            }
        }
        MessageServerContext.messageEventMulticaster = messageEventMulticaster;
        MonitorInitializer.registerDisruptorMetrics(messageEventMulticaster);
        // 可以通过spi 来进行加载,这里设计是为了以后扩展打包成boot-starter 时可以通过spi 的方式进行扩展监听器， 可以重写该方法自行实现
        log.debug("事件监听器加载完成");
    }

    /***
     * @author fzx
     * @description 加载协议分发处理器
     */
    @Override
    void loadProtocolProcessor() {
        log.debug("正在加载消息协议处理器......");
        Set<Class<?>> messageProtocolProcessorClazzSet = new HashSet<>();
        try {
            for (String messageProtocolProcessorScanPackagePath : MessageServerContext.serverProperties().getMessageProtocolProcessorScanPackagePaths()) {
                messageProtocolProcessorClazzSet.addAll(ClassScannerUtil.scanPackageBySuper(messageProtocolProcessorScanPackagePath, ProtocolDispatcherBiProcessor.class));
            }
        } catch (IOException e) {
            log.error("扫描消息协议处理器失败: {}", e.getMessage());
        }
        for (Class<?> messageProtocolProcessorClazz : messageProtocolProcessorClazzSet) {
            if (ProtocolDispatcherBiProcessor.class.isAssignableFrom(messageProtocolProcessorClazz)) {
                // 排除自身以及抽象类
                if (!ProtocolDispatcherBiProcessor.class.equals(messageProtocolProcessorClazz) && !Modifier.isAbstract(messageProtocolProcessorClazz.getModifiers())) {
                    Object processorObj = objenesis.newInstance(messageProtocolProcessorClazz);
                    // 消息协议处理器
                    if (processorObj instanceof ProtocolDispatcherBiProcessor messageProtocolProcessor) {
                        // 后面根据业务可以加入排序规则
                        MessageServerContext.protocolDispatcherProcessors.add(messageProtocolProcessor);
                        log.debug("消息协议处理器： {}", messageProtocolProcessor);
                    }
                }
            }
        }
        log.debug("消息协议处理器加载完成");
    }

    /***
     * @author fzx
     * @description 加载消息处理器
     */
    @SuppressWarnings("unchecked")
    @Override
    void loadMessageProcessor() {
        log.debug("正在加载消息处理器......");
        Set<Class<?>> messageProcessorClazzSet = new HashSet<>();
        try {
            for (String messageProcessorScanPackagePath : MessageServerContext.serverProperties().getMessageProcessorScanPackagePaths()) {
                messageProcessorClazzSet.addAll(ClassScannerUtil.scanPackageBySuper(messageProcessorScanPackagePath, BiProcessor.class));
            }
        } catch (IOException e) {
            log.error("扫描消息处理器失败: {}", e.getMessage());
        }
        // 过滤并获取所有的AbstractBaseProcessor的实现类集合
        Set<BiProcessor<ChannelHandlerContext,Packet>> biProcessorSet =  messageProcessorClazzSet.parallelStream().filter(processorClazz -> BiProcessor.class.isAssignableFrom(processorClazz) && !BiProcessor.class.equals(processorClazz) && !Modifier.isAbstract(processorClazz.getModifiers()) && !BiProcessorChainProxy.class.equals(processorClazz) && !DelegatingMessageProcessorChain.class.equals(processorClazz) && !DelegatingMessageContentProcessorChain.class.equals(processorClazz)).map(processorClazz-> (BiProcessor<ChannelHandlerContext, Packet>)objenesis.newInstance(processorClazz)).collect(Collectors.toSet());
        // 消息处理器继承了 AbstractMessageProcessor ，消息内容处理器继承了 AbstractBaseProcessor ，分别筛选该两个类的实现类，且 type为MessageType 和MessageContentType 的处理类集合
        List<AbstractMessageBiProcessor<? extends Number>> messageProcessorList = new ArrayList<>();
        List<AbstractBaseBiProcessor<? extends Number>> messageContentProcessorList = new ArrayList<>();
        for (BiProcessor<ChannelHandlerContext, Packet> biProcessor : biProcessorSet) {
            if (biProcessor instanceof AbstractMessageBiProcessor<?> messageProcessor  && messageProcessor.type() instanceof MessageType) {
                // 消息处理器
                messageProcessorList.add(messageProcessor);
            }else if (biProcessor instanceof AbstractBaseBiProcessor<?> baseProcessor && baseProcessor.type() instanceof MessageContentType) {
                // 消息内容处理器
                messageContentProcessorList.add(baseProcessor);
            }
        }
        // 消息 分别按照类型值分组，并排序
        messageProcessorList.stream().collect(Collectors.groupingBy(messageProcessor -> messageProcessor.type().getType())).forEach((messageTypeValue, messageProcessors)->{
            int messageProcessorSize = messageProcessors.size();
            if (messageProcessorSize == NumberConstant.NUMBER_1) {
                AbstractMessageBiProcessor<? extends Number> messageProcessor = messageProcessors.getFirst();
                MessageServerContext.messageProcessorCache.put(messageTypeValue, messageProcessor);
            }else if (messageProcessorSize > NumberConstant.NUMBER_1) {
                List<ProcessorChain<AbstractMessageBiProcessor<? extends Number>>> processorChains = new ArrayList<>();
                // 如果大于1，则转换为代理
                messageProcessors.stream().collect(Collectors.groupingBy(processor -> new MessageProtocol(processor.type().getProtocol(), processor.type().getProtocolVersion()))).forEach((protocolType, protocolTypeProcessors)->{
                    // 排序
                    OrderSortUtil.sort(protocolTypeProcessors);
                    // 构建代理
                    DelegatingMessageProcessorChain delegatingMessageProcessorChain = new DelegatingMessageProcessorChain(protocolType,  protocolTypeProcessors);
                    processorChains.add(delegatingMessageProcessorChain);
                });
                // 这里面的类型直接去取第一个
                BiProcessorChainProxy<AbstractMessageBiProcessor<? extends Number>> processorChainProxy = new BiProcessorChainProxy<>(processorChains, processorChains.getFirst().getProcessors().getFirst().type());
                MessageServerContext.messageProcessorCache.put(messageTypeValue, processorChainProxy);
            }
            log.debug("消息类型处理器({})： {}", messageTypeValue, MessageServerContext.messageProcessorCache.get(messageTypeValue));
        });

        // 消息内容，分别按照类型值分组，并排序
        messageContentProcessorList.stream().collect(Collectors.groupingBy(messageContentProcessor -> messageContentProcessor.type().getType())).forEach((messageContentTypeValue, messageContentProcessors)->{
            int messageContentProcessorSize = messageContentProcessors.size();
            if (messageContentProcessorSize == NumberConstant.NUMBER_1) {
                AbstractBaseBiProcessor<? extends Number> messageContentProcessor = messageContentProcessors.getFirst();
                MessageServerContext.messageContentProcessorCache.put(messageContentTypeValue, messageContentProcessor);
            }else if (messageContentProcessorSize > NumberConstant.NUMBER_1) {
                List<ProcessorChain<AbstractBaseBiProcessor<? extends Number>>> processorChains = new ArrayList<>();
                // 如果大于1，则转换为代理
                messageContentProcessors.stream().collect(Collectors.groupingBy(processor -> new MessageProtocol(processor.type().getProtocol(), processor.type().getProtocolVersion()))).forEach((protocolType, protocolTypeProcessors)->{
                    // 排序
                    OrderSortUtil.sort(protocolTypeProcessors);
                    // 构建代理
                    DelegatingMessageContentProcessorChain delegatingMessageContentProcessorChain =  new DelegatingMessageContentProcessorChain(protocolType,  protocolTypeProcessors);
                    processorChains.add(delegatingMessageContentProcessorChain);
                });
                // 这里面的类型直接去取第一个
                BiProcessorChainProxy<AbstractBaseBiProcessor<? extends Number>> processorChainProxy = new BiProcessorChainProxy<>(processorChains, processorChains.getFirst().getProcessors().getFirst().type());
                MessageServerContext.messageContentProcessorCache.put(messageContentTypeValue, processorChainProxy);
            }
            log.debug("消息内容类型处理器({})： {}",messageContentTypeValue ,MessageServerContext.messageContentProcessorCache.get(messageContentTypeValue));
        });
        log.debug("消息处理器加载完成");
    }

    /**
     * 加载消息发送拦截器
     */
    @Override
    void loadMessageInterceptor() {
        if (!MessageServerContext.serverProperties().isMessageInterceptorEnable()) {
            return;
        }
        log.debug("开始加载消息发送拦截器...");
        Set<Class<?>> interceptorClazzSet = new HashSet<>();
        try {
            for (String interceptorScanPackagePath : MessageServerContext.serverProperties().getMessageInterceptorScanPackagePaths()) {
                interceptorClazzSet.addAll(ClassScannerUtil.scanPackageBySuper(interceptorScanPackagePath, Interceptor.class));
            }
        } catch (IOException e) {
            log.error("扫描消息处理器失败: {}", e.getMessage());
        }
        Set<Class<?>> interceptorSet =  interceptorClazzSet.parallelStream().filter(interceptorClazz -> Interceptor.class.isAssignableFrom(interceptorClazz) && !Interceptor.class.equals(interceptorClazz) && !Modifier.isAbstract(interceptorClazz.getModifiers())).collect(Collectors.toSet());
        for (Class<?> interceptorClazz  : interceptorSet) {
            if (AbstractMessageInterceptor.class.isAssignableFrom(interceptorClazz)) {
                MessageServerContext.messageInterceptorChain.add((AbstractMessageInterceptor)objenesis.newInstance(interceptorClazz));
            }
        }
        // 排序
        OrderSortUtil.sort(MessageServerContext.messageInterceptorChain);
        log.debug("消息发送拦截器加载完成");
    }


    /**
     * 加载args 命令行参数属性配置
     */
    private MessageServerProperties loadArgsProperties(MessageServerProperties messageServerProperties, CommandLineArgs commandLineArgs) {
        commandLineArgs.getOptionNames().forEach(fieldName -> {
            Field field = ReflectUtil.findField(messageServerProperties.getClass(), fieldName);
            if (field != null) {
                ReflectUtil.setValueByField(field, messageServerProperties, commandLineArgs.getOptionValues(fieldName));
            }
        });
        return messageServerProperties;
    }

    /**
     * 解析处理命令行参数
     */
    private CommandLineArgs resolverArgs(String... args) {
        CommandLineArgs commandLineArgs = new CommandLineArgs();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String optionText = arg.substring(2);
                String optionName;
                String optionValue = null;
                int indexOfEqualsSign = optionText.indexOf('=');
                if (indexOfEqualsSign > -1) {
                    optionName = optionText.substring(0, indexOfEqualsSign);
                    optionValue = optionText.substring(indexOfEqualsSign + 1);
                } else {
                    optionName = optionText;
                }
                if (optionName.isEmpty()) {
                    log.error("非法命令行参数: {}", arg);
                    throw new IllegalArgumentException("非法命令行参数: " + arg);
                }
                commandLineArgs.addOptionArg(optionName, optionValue);
            } else {
                commandLineArgs.addNonOptionArg(arg);
            }
        }
        return commandLineArgs;
    }

    private static int messageListenerOrder(Class<?> listenerClass) {
        EventListener eventListener = listenerClass.getAnnotation(EventListener.class);
        return eventListener != null ? eventListener.order() : Integer.MAX_VALUE;
    }
}
