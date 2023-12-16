package com.ouyunc.im;

import com.alibaba.ttl.threadpool.TtlExecutors;
import com.ouyunc.im.channel.DefaultServerChannelInitializer;
import com.ouyunc.im.channel.DefaultSocketChannelInitializer;
import com.ouyunc.im.channel.ServerChannelInitializer;
import com.ouyunc.im.channel.SocketChannelInitializer;
import com.ouyunc.im.config.IMServerConfig;
import com.ouyunc.im.context.IMProcessContext;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.innerclient.DefaultIMInnerClient;
import com.ouyunc.im.innerclient.IMInnerClient;
import com.ouyunc.im.listener.AbstractImEventMulticaster;
import com.ouyunc.im.listener.DefaultImEventMulticaster;
import com.ouyunc.im.listener.IMEventMulticaster;
import com.ouyunc.im.listener.IMListener;
import com.ouyunc.im.processor.AbstractChatbotMessageProcessor;
import com.ouyunc.im.processor.AbstractMessageProcessor;
import com.ouyunc.im.processor.MessageProcessor;
import com.ouyunc.im.processor.content.AbstractMessageContentProcessor;
import com.ouyunc.im.utils.ClassScanner;
import com.ouyunc.im.utils.MapUtil;
import com.ouyunc.im.utils.SystemClock;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Log4J2LoggerFactory;
import jodd.util.concurrent.ThreadFactoryBuilder;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * @Author fangzhenxun
 * @Description: 抽象im服务，用于定义一些其他初始化方法
 **/
public abstract class AbstractIMServer implements IMServer {
    private static Logger log = LoggerFactory.getLogger(AbstractIMServer.class);

    private static Objenesis objenesis = new ObjenesisStd(true);

    /**
     * 服务启动对象
     */
    private final static ServerBootstrap bootstrap = new ServerBootstrap();
    ;

    /**
     * boss 线程组
     */
    private static EventLoopGroup bossGroup;

    /**
     * work线程组
     */
    private static EventLoopGroup workerGroup;


    /**
     * server channel 初始化默认值
     */
    private ServerChannelInitializer serverChannelInitializer = new DefaultServerChannelInitializer();

    /**
     * socket channel 初始化默认值
     */
    private SocketChannelInitializer socketChannelInitializer = new DefaultSocketChannelInitializer();


    /**
     * 集群内置客户端初始化, 默认内置客户端实现类
     */
    private IMInnerClient innerIMClient = new DefaultIMInnerClient();


    /**
     * =====================================开放属性设置，可以让用户自定义改造该实现======================================
     */
    public void setImClient(IMInnerClient innerIMClient) {
        this.innerIMClient = innerIMClient;
    }

    public void setServerChannelInitializer(ServerChannelInitializer serverChannelInitializer) {
        this.serverChannelInitializer = serverChannelInitializer;
    }

    public void setSocketChannelInitializer(SocketChannelInitializer socketChannelInitializer) {
        this.socketChannelInitializer = socketChannelInitializer;
    }

    /**
     * @return void
     * @Author fangzhenxun
     * @Description im服务启动入口
     */
    @Override
    public void start(String[] args) {
        log.info("IM开始启动,正在初始化........");
        // 注册关闭钩子
        registerShutdownHook();
        // 设置实现类到本地线程中
        IMServerContext.TTL_THREAD_LOCAL.set(this);
        // 加载配置
        IMServerContext.SERVER_CONFIG = loadProperties(args);
        // 加载事件监听器
        loadEventListener();
        // 加载消息处理器
        loadMessageProcessor();
        // 初始化IM服务
        initServer();
    }


    /**
     * @return void
     * @Author fangzhenxun
     * @Description 初始化im server服务&内置客户端（集群使用）
     */
    private void initServer() {
        IMServerConfig imServerConfig = IMServerContext.SERVER_CONFIG;
        final long startTimeStamp = SystemClock.now();
        // 集成log4j2
        InternalLoggerFactory.setDefaultFactory(Log4J2LoggerFactory.INSTANCE);
        // 配置boss 线程组&工作线程组
        bossGroup = new NioEventLoopGroup(imServerConfig.getBossThreads());
        workerGroup = new NioEventLoopGroup(imServerConfig.getWorkThreads());
        try {
            // 设置相关属性
            bootstrap.group(bossGroup, workerGroup)
                    // 通过反射拿到对应的处理通道类型
                    .channel(NioServerSocketChannel.class)
                    // boss 线程组处理器,handler在初始化时就会执行
                    .handler(serverChannelInitializer)
                    // worker线程组处理器,childHandler会在客户端成功connect后执行
                    .childHandler(socketChannelInitializer);


            // 设置boss 线程组相关的属性
            Map<ChannelOption, Object> channelOptionMap = imServerConfig.getChannelOptionMap();
            if (MapUtil.isNotEmpty(channelOptionMap)) {
                for (Map.Entry<ChannelOption, Object> channelOptionEntry : channelOptionMap.entrySet()) {
                    bootstrap.option(channelOptionEntry.getKey(), channelOptionEntry.getValue());
                }
            }

            // 针对workerGroup设置连接活动保持连接状态
            Map<ChannelOption, Object> childChannelOptionMap = imServerConfig.getChildChannelOptionMap();
            if (MapUtil.isNotEmpty(childChannelOptionMap)) {
                for (Map.Entry<ChannelOption, Object> childChannelOptionEntry : childChannelOptionMap.entrySet()) {
                    bootstrap.childOption(childChannelOptionEntry.getKey(), childChannelOptionEntry.getValue());
                }
            }
            // 因为bind() 是异步的，这里不用 bind().sync(); 而是添加监听器的方式进行回调
            ChannelFuture channelFuture = bootstrap.bind(imServerConfig.getPort());
            // 添加监听器来监听是否启动成功,做额外工作
            channelFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture bindFuture) throws Exception {
                    if (bindFuture.isDone()) {
                        if (bindFuture.isSuccess()) {
                            // =====================开始处理内置客户端用于做集群=================
                            if (imServerConfig.isClusterEnable()) {
                                innerIMClient.configure(imServerConfig);
                            }
                            log.info("IM server启动成功，其绑定地址:{} 端口号:{} 共花费:{} ms.", imServerConfig.getIp(), imServerConfig.getPort(), (SystemClock.now() - startTimeStamp));
                        } else {
                            log.error("IM server 启动失败！原因: {}", bindFuture.cause().getMessage());
                            throw new Exception(bindFuture.cause().getMessage());
                        }
                    }
                }
            });
            // 对关闭通道进行监听,不是立刻关闭,这里主要是为了优雅的关闭，将主线程阻塞处理
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            log.error("IM server 出现异常,原因：{}; 正在关闭服务...", e.getMessage());
            e.printStackTrace();
        } finally {
            // 优雅关闭
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }


    }



    /**
     * 查找入口类
     *
     * @return
     */
    protected static Class<?> deduceMainClass() {
        try {
            StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
            for (StackTraceElement stackTraceElement : stackTrace) {
                if ("main".equals(stackTraceElement.getMethodName())) {
                    return Class.forName(stackTraceElement.getClassName());
                }
            }
        } catch (ClassNotFoundException ex) {
            // Swallow and continue
        }
        return null;
    }


    /**
     * @param
     * @return com.ouyu.im.config.IMServerConfig
     * @Author fangzhenxun
     * @Description IM服务配置类
     */
    abstract IMServerConfig loadProperties(String... args);



    /**
     * @Author fangzhenxun
     * @Description 加载事件监听器
     * @param
     * @return void
     */
    protected void loadEventListener() {
        Set<Class<?>> classes = new HashSet<>();
        try {
            classes = ClassScanner.scanPackageBySuper(IMServerContext.SERVER_CONFIG.getApplicationMainClass().getPackage().getName(), IMListener.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (Class<?> cls : classes) {
            if (IMListener.class.isAssignableFrom(cls)) {
                // 排除自身以及抽象类
                if (!IMListener.class.equals(cls) && !Modifier.isAbstract(cls.getModifiers())) {
                    IMListener imListener = (IMListener) objenesis.newInstance(cls);
                    // 排除不是直接实现该接口的
                    DefaultImEventMulticaster imEventMulticaster = new DefaultImEventMulticaster();
                    // 这里配置线程池来处理，如果同步发送事件可以注释下面一行
                    imEventMulticaster.setTaskExecutor(TtlExecutors.getTtlExecutorService(new DefaultEventExecutorGroup(16, ThreadFactoryBuilder.create().setNameFormat("event-listener-%d").get())));
                    imEventMulticaster.addImListener(imListener);
                    IMServerContext.IM_EVENT_MULTICASTER = imEventMulticaster;
                }
            }
        }

    }

    /**
     * @Author fangzhenxun
     * @Description 加载消息处理器
     * @param
     * @return void
     */
    protected void loadMessageProcessor() {
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
                        IMProcessContext.MESSAGE_PROCESSOR.put(((AbstractMessageProcessor) messageProcessor).messageType().getValue(), ((AbstractMessageProcessor) messageProcessor));
                    }
                    if (AbstractMessageContentProcessor.class.isAssignableFrom(cls)) {
                        IMProcessContext.MESSAGE_CONTENT_PROCESSOR.put(((AbstractMessageContentProcessor) messageProcessor).messageContentType().type(), ((AbstractMessageContentProcessor) messageProcessor));
                    }
                    if (AbstractChatbotMessageProcessor.class.isAssignableFrom(cls)) {
                        // 排除自身以及抽象类
                        IMProcessContext.CHAT_BOT_PROCESSOR.add((AbstractChatbotMessageProcessor) messageProcessor);
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
                IMProcessContext.MESSAGE_PROCESSOR.put(((AbstractMessageProcessor) messageProcessor).messageType().getValue(), ((AbstractMessageProcessor) messageProcessor));
            }
            if (AbstractMessageContentProcessor.class.isAssignableFrom(cls)) {
                IMProcessContext.MESSAGE_CONTENT_PROCESSOR.put(((AbstractMessageContentProcessor) messageProcessor).messageContentType().type(), ((AbstractMessageContentProcessor) messageProcessor));
            }
            if (AbstractChatbotMessageProcessor.class.isAssignableFrom(cls)) {
                // 排除自身以及抽象类
                IMProcessContext.CHAT_BOT_PROCESSOR.add((AbstractChatbotMessageProcessor) messageProcessor);
            }
        }
        // 排序并整合
        IMProcessContext.CHAT_BOT_PROCESSOR = IMProcessContext.CHAT_BOT_PROCESSOR.stream().sorted(Comparator.comparingInt(AbstractChatbotMessageProcessor::order)).collect(Collectors.toList());
        for (int i = 0; i < IMProcessContext.CHAT_BOT_PROCESSOR.size(); i++) {
            if (i == IMProcessContext.CHAT_BOT_PROCESSOR.size() - 1) {
                IMProcessContext.CHAT_BOT_PROCESSOR.get(i).setNextHandler(null);
            } else {
                IMProcessContext.CHAT_BOT_PROCESSOR.get(i).setNextHandler(IMProcessContext.CHAT_BOT_PROCESSOR.get(i + 1));
            }
        }
    }

    /**
     * @return void
     * @Author fangzhenxun
     * @Description 主动注销服务
     */
    @Override
    public void stop() {
        log.error("IM server 开始注销程序...");
        System.exit(0);
    }


    /**
     * @return void
     * @Author fangzhenxun
     * @Description 监听注销服务钩子
     */
    private void registerShutdownHook() {
        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // 在关闭钩子中执行收尾工作
                // 注意事项：
                // 1.在这里执行的动作不能耗时太久
                // 2.不能在这里再执行注册，移除关闭钩子的操作
                // 3 不能在这里调用System.exit()
                // 优雅关闭
                log.info("IM正在注销...");
                if (bossGroup != null && workerGroup != null) {
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                }
                innerIMClient.stop();
                log.info("IM注销完成");
            }
        });
    }


}
