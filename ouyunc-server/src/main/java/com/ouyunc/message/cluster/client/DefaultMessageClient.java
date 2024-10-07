package com.ouyunc.message.cluster.client;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.thread.MessageClusterSynAckThread;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @Author fzx
 * @Description: 默认 IM 内置客户端实现类
 **/
public class DefaultMessageClient extends AbstractMessageClient {
    private static final Logger log = LoggerFactory.getLogger(DefaultMessageClient.class);

    /***
     * 定时任务事件执行器
     */
    private static final ScheduledExecutorService SCHEDULED_EVENT_EXECUTORS = Executors.newSingleThreadScheduledExecutor(new BasicThreadFactory.Builder().namingPattern("cluster-message-client-pool-%d").build());



    /**
     * @Author fzx
     * @Description 做一些初始化后的处理，内部客户端心跳
     */
    @Override
    void afterPropertiesSet() {
        // 初始化客户端之后做的事情，对内置客户端的包活处理，及时更新处理本地服务注册表,定时任务处理
        SCHEDULED_EVENT_EXECUTORS.scheduleWithFixedDelay(new MessageClusterSynAckThread(), MessageConstant.ZERO, MessageServerContext.serverProperties().getClusterClientHeartbeatInterval(), TimeUnit.SECONDS);
    }


}
