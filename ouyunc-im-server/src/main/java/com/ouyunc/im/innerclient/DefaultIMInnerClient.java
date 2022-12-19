package com.ouyunc.im.innerclient;

import com.ouyunc.im.config.IMServerConfig;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.innerclient.pool.IMInnerClientPool;
import com.ouyunc.im.thread.IMInnerClientHeartbeatThread;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * @Author fangzhenxun
 * @Description: 默认 IM 内置客户端实现类
 * @Version V3.0
 **/
public class DefaultIMInnerClient extends AbstractIMInnerClient {
    private static Logger log = LoggerFactory.getLogger(DefaultIMInnerClient.class);

    private static final EventExecutorGroup eventExecutors= new DefaultEventExecutorGroup(1);

    /**
     * @Author fangzhenxun
     * @Description 将内置客户端的一些基本信息进行初始化
     * @param serverConfig
     * @return void
     */
    @Override
    void initClient(IMServerConfig serverConfig) {
        // 判断全局服务缓存使用哪种存储
        IMInnerClientPool.init(serverConfig);
    }

    /**
     * @Author fangzhenxun
     * @Description 做一些初始化后的处理，内部客户端心跳
     * @param
     * @return void
     */
    @Override
    void afterPropertiesSet() {
        // 初始化客户端之后做的事情，对内置客户端的包活处理，及时更新处理本地服务注册表,定时任务处理
        eventExecutors.scheduleWithFixedDelay(new IMInnerClientHeartbeatThread(), 0, IMServerContext.SERVER_CONFIG.getClusterInnerClientHeartbeatInterval(), TimeUnit.SECONDS);
    }

    /**
     * @Author fangzhenxun
     * @Description 停止内部客户端
     * @return void
     */
    @Override
    public void stop() {
        // 注销内置客户端
        IMInnerClientPool.stop();
        // 注销额外执行器
        eventExecutors.shutdownGracefully();
    }
}
