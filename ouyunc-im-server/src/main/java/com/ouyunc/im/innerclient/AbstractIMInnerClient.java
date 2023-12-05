package com.ouyunc.im.innerclient;

import com.ouyunc.im.config.IMServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 抽象内部客户端
 **/
public abstract class AbstractIMInnerClient implements IMInnerClient {
    private static Logger log = LoggerFactory.getLogger(AbstractIMInnerClient.class);


    /**
     * @param serverConfig
     * @return void
     * @Author fangzhenxun
     * @Description 初始化内置客户端
     */
    abstract void initClient(IMServerConfig serverConfig);

    /**
     * @param
     * @return void
     * @Author fangzhenxun
     * @Description 初始化后做善后操作
     */
    abstract void afterPropertiesSet();

    /**
     * @param serverConfig
     * @return void
     * @Author fangzhenxun
     * @Description 启动内置客户端，作用于集群使用
     */
    @Override
    public void configure(IMServerConfig serverConfig) {
        log.info("集群模式已开启，正在配置内置客户端...");
        initClient(serverConfig);
        afterPropertiesSet();
    }
}
