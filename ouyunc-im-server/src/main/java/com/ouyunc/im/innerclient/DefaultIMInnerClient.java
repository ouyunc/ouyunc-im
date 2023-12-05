package com.ouyunc.im.innerclient;

import com.alibaba.ttl.threadpool.TtlExecutors;
import com.ouyunc.im.config.IMServerConfig;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.innerclient.pool.IMInnerClientPool;
import com.ouyunc.im.thread.IMInnerClientHeartbeatThread;
import jodd.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @Author fangzhenxun
 * @Description: 默认 IM 内置客户端实现类
 **/
public class DefaultIMInnerClient extends AbstractIMInnerClient {
    private static Logger log = LoggerFactory.getLogger(DefaultIMInnerClient.class);

    private static final ScheduledExecutorService EVENT_EXECUTORS = TtlExecutors.getTtlScheduledExecutorService(Executors.newScheduledThreadPool(16, ThreadFactoryBuilder.create().setNameFormat("inner-client-pool-%d").get()));

    /**
     * @param serverConfig
     * @return void
     * @Author fangzhenxun
     * @Description 将内置客户端的一些基本信息进行初始化
     */
    @Override
    void initClient(IMServerConfig serverConfig) {
        // 判断全局服务缓存使用哪种存储
        IMInnerClientPool.init(serverConfig);
    }

    /**
     * @param
     * @return void
     * @Author fangzhenxun
     * @Description 做一些初始化后的处理，内部客户端心跳
     */
    @Override
    void afterPropertiesSet() {
        // 初始化客户端之后做的事情，对内置客户端的包活处理，及时更新处理本地服务注册表,定时任务处理
        EVENT_EXECUTORS.scheduleWithFixedDelay(new IMInnerClientHeartbeatThread(), 0, IMServerContext.SERVER_CONFIG.getClusterInnerClientHeartbeatInterval(), TimeUnit.SECONDS);
    }

    /**
     * @return void
     * @Author fangzhenxun
     * @Description 停止内部客户端
     */
    @Override
    public void stop() {
        // 注销内置客户端
        IMInnerClientPool.stop();
        // 注销额外执行器
        EVENT_EXECUTORS.shutdown();
    }
}
