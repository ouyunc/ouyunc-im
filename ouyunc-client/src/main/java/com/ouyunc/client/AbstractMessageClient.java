package com.ouyunc.client;

import com.ouyunc.client.pool.MessageClientPool;
import com.ouyunc.core.properties.MessageProperties;
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
     void initClient(MessageProperties messageProperties) {
         // 初始化客户端连接池
         MessageClientPool.init(messageProperties);
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
    public void configure(MessageProperties messageServerProperties) {
        initClient(messageServerProperties);
        afterPropertiesSet();
    }

    /***
     * @author fzx
     * @description 停止内置客户端
     */
    @Override
    public void stop() {
        log.error("正在内置客户端......");
    }
}
