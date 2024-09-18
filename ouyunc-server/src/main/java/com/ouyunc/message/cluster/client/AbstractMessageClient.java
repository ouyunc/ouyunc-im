package com.ouyunc.message.cluster.client;

import com.ouyunc.message.cluster.client.pool.MessageClientPool;
import com.ouyunc.message.properties.MessageServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 抽象内部客户端
 **/
public abstract class AbstractMessageClient implements MessageClient {
    private static final Logger log = LoggerFactory.getLogger(AbstractMessageClient.class);


    /**
     * @Author fzx
     * @Description 初始化内置客户端
     */
     void initClient(MessageServerProperties messageServerProperties) {
         // 初始化客户端连接池
         MessageClientPool.init(messageServerProperties);
     };

    /**
     * @Author fzx
     * @Description 初始化后做善后操作
     */
    abstract void afterPropertiesSet();

    /**
     * @Author fzx
     * @Description 配置内置客户端，作用于集群使用
     */
    @Override
    public void configure(MessageServerProperties messageServerProperties) {
        log.debug("集群模式已开启，正在配置内置客户端......");
        initClient(messageServerProperties);
        afterPropertiesSet();
        log.debug("集群模式配置内置客户端完成......");
    }

    /***
     * @author fzx
     * @description 停止内置客户端
     */
    @Override
    public void stop() {
        log.error("正在注销集群内置客户端......");
    }
}
