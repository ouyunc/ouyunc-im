package com.ouyu.im;


import cn.hutool.core.map.MapUtil;
import com.ouyu.im.channel.DefaultServerChannelInitializer;
import com.ouyu.im.channel.DefaultSocketChannelInitializer;
import com.ouyu.im.channel.ServerChannelInitializer;
import com.ouyu.im.channel.SocketChannelInitializer;
import com.ouyu.im.config.IMServerConfig;
import com.ouyu.im.constant.ImConstant;
import com.ouyu.im.context.IMServerContext;
import com.ouyu.im.innerclient.DefaultIMClient;
import com.ouyu.im.innerclient.IMClient;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Log4J2LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

/**
 * @Author fangzhenxun
 * @Description: 抽象im服务，用于定义一些其他初始化方法
 * @Version V1.0
 **/
public abstract class AbstractIMServer implements IMServer{
    private static Logger log = LoggerFactory.getLogger(AbstractIMServer.class);

    /**
     * boss 线程组
     */
    private EventLoopGroup bossGroup;

    /**
     * work线程组
     */
    private EventLoopGroup workerGroup;

    /**
     * 集群内置客户端初始化, 默认内置客户端实现类
     */
    private IMClient imClient = new DefaultIMClient();

    /**
     * server channel 初始化默认值
     */
    private ServerChannelInitializer serverChannelInitializer = new DefaultServerChannelInitializer();

    /**
     * socket channel 初始化默认值
     */
    private SocketChannelInitializer socketChannelInitializer = new DefaultSocketChannelInitializer();

    public void setImClient(IMClient imClient) {
        this.imClient = imClient;
    }

    public void setServerChannelInitializer(ServerChannelInitializer serverChannelInitializer) {
        this.serverChannelInitializer = serverChannelInitializer;
    }

    public void setSocketChannelInitializer(SocketChannelInitializer socketChannelInitializer) {
        this.socketChannelInitializer = socketChannelInitializer;
    }


    /**
     * @Author fangzhenxun
     * @Description IM服务配置类
     * @param
     * @return com.ouyu.im.config.IMServerConfig
     */
    abstract IMServerConfig loadConfigProperties();


    /**
     * @Author fangzhenxun
     * @Description 初始化im服务&内置客户端（集群使用）
     * @return void
     */
    private void initServer(final IMServerConfig serverConfig) {

        log.info("IM开始启动,正在初始化........");
        final long startTimeStamp = System.currentTimeMillis();

        // 集成log4j2
        InternalLoggerFactory.setDefaultFactory(Log4J2LoggerFactory.INSTANCE);
        // 创建启动服务对象
        ServerBootstrap bootstrap = new ServerBootstrap();
        // 配置boss 线程组&工作线程组
        bossGroup = new NioEventLoopGroup(serverConfig.getBossThreads());
        workerGroup = new NioEventLoopGroup(serverConfig.getWorkThreads());
        try{
            // 设置相关属性
            bootstrap.group(bossGroup, workerGroup)
                    // 通过反射拿到对应的处理通道类型
                    .channel(NioServerSocketChannel.class)
                    // boss 线程组处理器,handler在初始化时就会执行
                    .handler(serverChannelInitializer)
                    // worker线程组处理器,childHandler会在客户端成功connect后执行
                    .childHandler(socketChannelInitializer);

            // 设置boss 线程组相关的属性
            Map<ChannelOption, Object> channelOptionMap = serverConfig.getChannelOptionMap();
            if (MapUtil.isNotEmpty(channelOptionMap)) {
                for (Map.Entry<ChannelOption, Object> channelOptionEntry : channelOptionMap.entrySet()) {
                    bootstrap.option(channelOptionEntry.getKey(), channelOptionEntry.getValue());
                }
            }

            // 针对workerGroup设置连接活动保持连接状态
            Map<ChannelOption, Object> childChannelOptionMap = serverConfig.getChildChannelOptionMap();
            if (MapUtil.isNotEmpty(childChannelOptionMap)) {
                for (Map.Entry<ChannelOption, Object> childChannelOptionEntry : childChannelOptionMap.entrySet()) {
                    bootstrap.childOption(childChannelOptionEntry.getKey(), childChannelOptionEntry.getValue());
                }
            }

            // 异步绑定一个端口，并启动
            log.info("当前绑定端口号：{}", serverConfig.getPort());
            ChannelFuture channelFuture = bootstrap.bind(serverConfig.getPort());
            // 添加监听器来监听是否启动成功
            channelFuture.addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture startFuture) throws Exception {
                    if (startFuture.isSuccess()) {
                        // =====================开始处理内置客户端用于做集群=================
                        if (serverConfig.isClusterEnable()) {
                            imClient.configure(serverConfig);
                        }
                        log.info("IM启动成功,共花费{} ms", (System.currentTimeMillis()-startTimeStamp));
                    }else {
                        throw new Exception(startFuture.cause().getMessage());
                    }
                }
            });
            // 对关闭通道进行监听,不是立刻关闭,这里主要是为了优雅的关闭，将主线程阻塞处理
            channelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            log.error("IM服务出现异常,原因：{}; 正在关闭服务...", e.getMessage());
        } finally {
            // 优雅关闭
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }


    /**
     * @Author fangzhenxun
     * @Description im服务启动入口
     * @return void
     */
    public void start() {
        IMServerConfig imServerConfig = loadConfigProperties();
        // 给IM 设置配置文件的属性
        IMServerContext.SERVER_CONFIG = imServerConfig;
        try {
            IMServerContext.LOCAL_ADDRESS = InetAddress.getLocalHost().getHostAddress() + ImConstant.COLON_SPLIT + imServerConfig.getPort();
        } catch (UnknownHostException e) {
            log.error("start获取本地地址失败！");
            e.printStackTrace();
        }
        registerShutdownHook();
        // 初始化IM服务
        initServer(imServerConfig);
    }

    /**
     * @Author fangzhenxun
     * @Description 注册服务关闭钩子
     * @return void
     */
    public void registerShutdownHook() {
        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(){
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
                imClient.stop();
                log.info("IM注销完成");
            }
        });
    }

    /**
     * @Author fangzhenxun
     * @Description 停止/关闭im服务
     * @param
     * @return void
     */
    public void stop(){
        System.exit(0);
    }


}
