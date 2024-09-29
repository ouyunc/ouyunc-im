package com.ouyunc.message;

import com.ouyunc.base.constant.enums.DeviceTypeEnum;
import com.ouyunc.core.listener.event.ServerStartupEvent;
import com.ouyunc.message.banner.MessageBanner;
import com.ouyunc.message.channel.DefaultServerChannelInitializer;
import com.ouyunc.message.channel.DefaultSocketChannelInitializer;
import com.ouyunc.message.channel.ServerChannelInitializer;
import com.ouyunc.message.channel.SocketChannelInitializer;
import com.ouyunc.message.cluster.client.DefaultMessageClient;
import com.ouyunc.message.cluster.client.MessageClient;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.convert.BinaryWebSocketFramePacketConverter;
import com.ouyunc.message.convert.MqttMessagePacketConverter;
import com.ouyunc.message.convert.PacketPacketConverter;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Log4J2LoggerFactory;
import org.apache.commons.collections4.MapUtils;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Map;

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
    private final static ServerBootstrap bootstrap = new ServerBootstrap();

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
    void afterPropertiesSet() {};

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
     * @description 初始化服务之前做些处理，可以对上下文属性值进行改变
     */
    void beforeInitServer() {
        // 添加协议包转换器
        MessageServerContext.addPacketConverterList(PacketPacketConverter.INSTANCE);
        MessageServerContext.addPacketConverterList(BinaryWebSocketFramePacketConverter.INSTANCE);
        MessageServerContext.addPacketConverterList(MqttMessagePacketConverter.INSTANCE);
        // 添加默认设备类型
        MessageServerContext.addDeviceType(DeviceTypeEnum.class);
        // 设置路由器
        // 可以打印所有支持的消息类型，以及消息内容类型
    }
    /***
     * @author fzx
     * @description 开始服务
     * @param args  启动参数
     */
    @Override
    public void start(String[] args) {
        log.debug("message开始启动,正在初始化......");
        // 打印banner
        MessageBanner.printBanner(System.out);
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
        // 系统退出，会触发服务关闭钩子，从而释放资源并关闭程序
        System.exit(0);
    }


    /***
     * @author fzx
     * @description 初始化服务
     */
    @SuppressWarnings({"rawtypes","unchecked"})
    protected void initServer() {
        log.debug("开始初始化核心message服务......");
        final long startTimeStamp = Clock.systemUTC().millis();
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
            channelFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture bindFuture) throws Exception {
                    if (bindFuture.isDone()) {
                        if (bindFuture.isSuccess()) {
                            // =====================开始处理内置客户端用于做集群=================
                            if (MessageServerContext.serverProperties().isClusterEnable()) {
                                messageClient.configure(MessageServerContext.serverProperties());
                            }
                            log.debug("核心message服务初始化完成");
                            MessageServerContext.publishEvent(new ServerStartupEvent(MessageServerContext.serverProperties().getLocalServerAddress()), true);
                            log.debug("IM server启动成功，其绑定地址:{} 端口号:{} 共花费:{} ms.", MessageServerContext.serverProperties().getIp(), MessageServerContext.serverProperties().getPort(), (Clock.systemUTC().millis() - startTimeStamp));
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
            // 在关闭钩子中执行收尾工作
            // 注意事项：
            // 1.在这里执行的动作不能耗时太久
            // 2.不能在这里再执行注册，移除关闭钩子的操作
            // 3 不能在这里调用System.exit()
            // 优雅关闭
            log.error("Message server 正在注销......");
            if (bossGroup != null && workerGroup != null) {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
            // 停止内部消息客户端
            messageClient.stop();
            log.error("Message server注销完成");
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
