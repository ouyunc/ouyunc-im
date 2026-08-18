package com.ouyunc.message;

import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.constant.enums.DeviceTypeEnum;
import com.ouyunc.base.constant.enums.LuaScriptEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.listener.MessageEventMulticaster;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.message.banner.MessageBanner;
import com.ouyunc.message.channel.DefaultServerChannelInitializer;
import com.ouyunc.message.channel.DefaultSocketChannelInitializer;
import com.ouyunc.message.channel.ServerChannelInitializer;
import com.ouyunc.message.channel.SocketChannelInitializer;
import com.ouyunc.message.cluster.client.DefaultMessageClient;
import com.ouyunc.message.cluster.client.MessageClient;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.http.HttpRequestDispatcher;
import com.ouyunc.message.convert.BinaryWebSocketFramePacketConverter;
import com.ouyunc.message.convert.MqttMessagePacketConverter;
import com.ouyunc.message.convert.PacketPacketConverter;
import com.ouyunc.message.monitor.ResourceMonitor;
import com.ouyunc.message.schedule.ScheduleTimer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Log4J2LoggerFactory;
import org.apache.commons.collections4.MapUtils;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author fzx
 * @Description: 抽象message服务，用于定义一些其他初始化方法
 **/
public abstract class AbstractMessageServer implements MessageServer {
    private static final Logger log = LoggerFactory.getLogger(AbstractMessageServer.class);

    protected static final Objenesis objenesis = new ObjenesisStd(true);

    /**
     * 服务启动对象
     */
    private static final ServerBootstrap bootstrap = new ServerBootstrap();

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
    private  MessageClient messageClient = new DefaultMessageClient();

    /**
     * {@link #stop()} 与 JVM shutdown hook 可能先后触发，整段优雅关闭只执行一次。
     */
    private final AtomicBoolean gracefulShutdownDone = new AtomicBoolean(false);

    /***
     * @author fzx
     * @description 设置集群内置客户端
     */
    public void setMessageClient(MessageClient messageClient) {
        this.messageClient = messageClient;
    }

    /***
     * @author fzx
     * @description 设置server channel 初始化器
     */
    public void setServerChannelInitializer(ServerChannelInitializer serverChannelInitializer) {
        this.serverChannelInitializer = serverChannelInitializer;
    }

    /***
     * @author fzx
     * @description 设置socket channel 初始化器
     */
    public void setSocketChannelInitializer(SocketChannelInitializer socketChannelInitializer) {
        this.socketChannelInitializer = socketChannelInitializer;
    }

    /**
     * @Author fzx
     * @Description IM服务配置类，现在直接读取本地配置文件；
     * 后续整合到spring 项目中，直接集成AbstractMessageServer 然后重写该方法的实现，从spring容器或者配置中心获取属性值即可
     */
    abstract void loadProperties(String... args);

    /***
     * @author fzx
     * @description 预留方法，用于在属性初始化后执行一些操作
     */
    void afterPropertiesSet() {}

    /***
     * @author fzx
     * @description 加载事件监听器
     */
    abstract void loadEventListener();

    /***
     * @author fzx
     * @description 加载协议分发处理器
     */
    abstract void loadProtocolProcessor();

    /***
     * @author fzx
     * @description 加载消息处理器
     */
    abstract void loadMessageProcessor();


    /***
     * @author fzx
     * @description 加载发送消息拦截器
     */
    abstract void loadMessageInterceptor();


    /***
     * @author fzx
     * @description 初始化服务之前做些处理，可以对上下文属性值进行改变
     */
    void beforeInitServer() {
        // 添加协议包转换器
        MessageServerContext.addPacketConverterList(List.of(PacketPacketConverter.INSTANCE, BinaryWebSocketFramePacketConverter.INSTANCE,MqttMessagePacketConverter.INSTANCE));
        // 添加默认设备类型，这里可以改成从redis 获取，与appKey 进行绑定，由appKey来自定义所支持的设备类型，如果appKey 没有指定支持的设备类型，则走默认设备类型
        MessageServerContext.addDeviceType(DeviceTypeEnum.class);
        // 发布预加载lua脚本事件
        MessageServerContext.publishEvent(new MessageEvent(LuaScriptEnum.values(), MessageEventTypeEnum.PRELOAD_LUA_SCRIPT), true);



    }

    /**
     * EventLoopGroup 优雅关闭等待上限（秒）
     */
    private static final long NETTY_SHUTDOWN_AWAIT_SECONDS = 15L;

    /**
     * 统一优雅关闭：摘流、SERVER_STOP、监控/时间轮、HTTP、Netty、集群客户端、Disruptor 环、全局线程池。
     *
     * @return 本次调用是否实际执行了关闭序列（已被其它路径执行过则返回 false）
     */
    private boolean runGracefulShutdownOnce() {
        if (!gracefulShutdownDone.compareAndSet(false, true)) {
            return false;
        }
        try {
            // 1. 摘流：拒绝新登录，/ready → 503
            MessageServerContext.enterDrainMode();
            // 2. SERVER_STOP（同步）：通知客户端主动断开 → 宽限期 → 强制关残留 → Redis 订阅/连接数定时任务清理
            MessageServerContext.publishEvent(new MessageEvent(this, MessageEventTypeEnum.SERVER_STOP), false);
            // 3. 停止资源监控（调度任务随 ThreadPoolManager 一并结束）
            ResourceMonitor.stopMonitoring();
            // 4. 停止 QoS 等 HashedWheelTimer，取消未完成超时
            ScheduleTimer.stop();
            // 5. HTTP 业务线程池
            HttpRequestDispatcher.shutdownHttpBusinessExecutor();
            // 6. 对外 Netty，等待关闭完成
            awaitEventLoopGroupShutdown(bossGroup, "bossGroup");
            awaitEventLoopGroupShutdown(workerGroup, "workerGroup");
            // 7. 集群内置客户端连接池
            if (MessageServerContext.serverProperties().isClusterEnable() && messageClient != null) {
                messageClient.stop();
            }
            // 8. Disruptor 环形队列（须在 ThreadPoolManager 之前，环有独立消费线程）
            shutdownEventMulticaster();
            // 9. 全局业务线程池
            ThreadPoolManager.shutdownAll();
        } catch (Throwable t) {
            log.error("优雅关闭过程异常: {}", t.getMessage(), t);
        }
        return true;
    }

    /**
     * 关闭事件多播器并释放各等级 Disruptor RingBuffer。
     */
    private static void shutdownEventMulticaster() {
        MessageEventMulticaster multicaster = MessageServerContext.messageEventMulticaster;
        if (multicaster == null) {
            return;
        }
        try {
            multicaster.removeAllMessageListeners();
            log.warn("事件多播器 / Disruptor 环形队列已关闭");
        } catch (Exception e) {
            log.warn("关闭事件多播器异常: {}", e.getMessage());
        }
    }

    /**
     * 等待 Netty EventLoopGroup 优雅关闭。
     */
    private void awaitEventLoopGroupShutdown(EventLoopGroup group, String name) {
        if (group == null) {
            return;
        }
        try {
            Future<?> future = group.shutdownGracefully();
            if (!future.await(NETTY_SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Netty {} 在 {}s 内未完全关闭", name, NETTY_SHUTDOWN_AWAIT_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待 Netty {} 关闭被中断", name);
            group.shutdownGracefully();
        }
    }

    /***
     * @author fzx
     * @description 开始服务
     * @param args  启动参数
     */
    @Override
    public void start(String[] args) {
        // 打印banner
        MessageBanner.printBanner(System.out);
        log.debug("message开始启动,正在初始化......");
        // 注册关闭钩子
        registerShutdownHook();
        // 设置服务实例
        cacheMessageServer();
        // 加载配置
        loadProperties(args);
        // 属性加载之后执行一些操作，可以对属性进行改变
        afterPropertiesSet();
        // 加载事件监听器
        loadEventListener();
        // 加载协议分发处理器
        loadProtocolProcessor();
        // 加载消息处理器
        loadMessageProcessor();
        // 加载发送消息拦截器
        loadMessageInterceptor();
        // 初始化服务之前做些操作，可以对上下文属性值进行改变
        beforeInitServer();
        // 初始化IM服务
        initServer();
    }



    /***
     * @author fzx
     * @description 停止服务
     */
    @Override
    public void stop() {
        log.error("IM server 开始注销程序...");
        if (runGracefulShutdownOnce()) {
            log.error("IM server 注销流程已完成, 即将退出");
        }
        // 会触发 shutdown hook；其中 runGracefulShutdownOnce 已为幂等，不会重复收尾
        System.exit(0);
    }


    /***
     * @author fzx
     * @description 初始化服务
     */
    @SuppressWarnings({"rawtypes","unchecked"})
    protected void initServer() {
        log.debug("开始初始化核心message服务......");
        final long startTimeStamp = TimeUtil.currentTimeMillis();
        // 集成log4j2
        InternalLoggerFactory.setDefaultFactory(Log4J2LoggerFactory.INSTANCE);
        // 配置boss 线程组&工作线程组
        bossGroup = new NioEventLoopGroup(MessageServerContext.serverProperties().getBossThreads());
        workerGroup = new NioEventLoopGroup(MessageServerContext.serverProperties().getWorkThreads());
        try {
            // 设置相关属性
            bootstrap.group(bossGroup, workerGroup)
                    // 通过反射拿到对应的处理通道类型
                    .channel(NioServerSocketChannel.class)
                    // boss 线程组处理器,handler在初始化时就会执行
                    .handler(serverChannelInitializer)
                    // 本地地址
                    .localAddress(MessageServerContext.serverProperties().getIp(), MessageServerContext.serverProperties().getPort())
                    // worker线程组处理器,childHandler会在客户端成功connect后执行
                    .childHandler(socketChannelInitializer);
            // 设置boss 线程组相关的属性
            Map<ChannelOption, Object> channelOptionMap = MessageServerContext.serverProperties().getChannelOptionMap();
            if (MapUtils.isNotEmpty(channelOptionMap)) {
                for (Map.Entry<ChannelOption, Object> channelOptionEntry : channelOptionMap.entrySet()) {
                    bootstrap.option(channelOptionEntry.getKey(), channelOptionEntry.getValue());
                }
            }
            // 针对workerGroup设置连接活动保持连接状态
            Map<ChannelOption, Object> childChannelOptionMap = MessageServerContext.serverProperties().getChildChannelOptionMap();
            if (MapUtils.isNotEmpty(childChannelOptionMap)) {
                for (Map.Entry<ChannelOption, Object> childChannelOptionEntry : childChannelOptionMap.entrySet()) {
                    bootstrap.childOption(childChannelOptionEntry.getKey(), childChannelOptionEntry.getValue());
                }
            }
            // 因为bind() 是异步的，这里不用 bind().sync(); 而是添加监听器的方式进行回调
            ChannelFuture channelFuture = bootstrap.bind();
            // 添加监听器来监听是否启动成功,做额外工作
            channelFuture.addListener((ChannelFutureListener) bindFuture -> {
                if (bindFuture.isDone()) {
                    if (bindFuture.isSuccess()) {
                        // =====================开始处理内置客户端用于做集群=================
                        if (MessageServerContext.serverProperties().isClusterEnable()) {
                            messageClient.configure(MessageServerContext.serverProperties());
                        }
                        log.debug("核心message服务初始化完成");
                        MessageServerContext.publishEvent(new MessageEvent(MessageServerContext.serverProperties().getLocalServerAddress(), MessageEventTypeEnum.SERVER_STARTUP), true);
                        log.debug("IM server启动成功，其绑定地址:{} 端口号:{} 共花费:{} ms.", MessageServerContext.serverProperties().getIp(), MessageServerContext.serverProperties().getPort(), (TimeUtil.currentTimeMillis() - startTimeStamp));
                    } else {
                        log.error("IM server 启动失败！原因: {}", bindFuture.cause().getMessage());
                        throw new MessageException(bindFuture.cause().getMessage());
                    }
                }
            });
            // 对关闭通道进行监听,不是立刻关闭,这里主要是为了优雅的关闭，将主线程阻塞处理
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            log.error("Message server 出现异常,原因：{}; 正在关闭服务...", e.getMessage());
        } finally {
            // 优雅关闭
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    /**
     * @Author fzx
     * @Description 监听注销服务钩子
     */
    private void registerShutdownHook() {
        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // 注意事项：1.不宜耗时过久 2.勿再注册/移除钩子 3.勿调用 System.exit
            log.error("Message server shutdown hook 触发");
            if (runGracefulShutdownOnce()) {
                log.error("Message server 注销完成");
            }
        }));
    }

    /***
     * @author fzx
     * @description 设置message Server
     */
    private void cacheMessageServer() {
        MessageServerContext.server = this;
    }



}
