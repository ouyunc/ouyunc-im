package com.ouyu.im.innerclient;

import com.ouyu.im.config.IMServerConfig;
import com.ouyu.im.context.IMServerContext;
import com.ouyu.im.innerclient.pool.IMClientPool;
import com.ouyu.im.thread.IMClientRegisterThread;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * @Author fangzhenxun
 * @Description: 默认 IM 内置客户端实现类
 * @Version V1.0
 **/
public class DefaultIMClient extends AbstractIMClient{
    private static Logger log = LoggerFactory.getLogger(DefaultIMClient.class);
    private static final EventExecutorGroup eventExecutors= new DefaultEventExecutorGroup(1);

    /**
     * @Author fangzhenxun
     * @Description 将内置客户端的一些基本信息进行初始化
     * @param serverConfig
     * @return void
     */
    void initClient(IMServerConfig serverConfig) {
        // 判断全局服务缓存使用哪种存储
        IMClientPool.init(serverConfig);
    }

    /**
     * @Author fangzhenxun
     * @Description 做一些初始化后的处理
     * @param
     * @return void
     */
    @Override
    void afterPropertiesSet() {
        // 初始化客户端之后做的事情
        eventExecutors.scheduleWithFixedDelay(new IMClientRegisterThread(), 0, IMServerContext.SERVER_CONFIG.getClusterServerInitRegisterPeriod(), TimeUnit.SECONDS);
    }

    /**
     * @Author fangzhenxun
     * @Description 停止内部客户端
     * @return void
     */
    @Override
    public void stop() {
        // 注销内置客户端
        IMClientPool.stop();
        // 注销额外执行器
        eventExecutors.shutdownGracefully();
    }
}
