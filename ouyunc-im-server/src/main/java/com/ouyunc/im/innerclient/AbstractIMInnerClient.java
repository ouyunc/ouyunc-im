package com.ouyunc.im.innerclient;

import com.ouyunc.im.config.IMServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 抽象内部客户端
 * @Version V3.0
 **/
public abstract class AbstractIMInnerClient implements IMInnerClient {
    private static Logger log = LoggerFactory.getLogger(AbstractIMInnerClient.class);


    /**
     * @Author fangzhenxun
     * @Description 初始化内置客户端
     * @param serverConfig
     * @return void
     */
    abstract void initClient(IMServerConfig serverConfig);

    /**
     * @Author fangzhenxun
     * @Description 初始化后做善后操作
     * @param
     * @return void
     */
    abstract void afterPropertiesSet();

    /**
     * @Author fangzhenxun
     * @Description 启动内置客户端，作用于集群使用
     * @param serverConfig
     * @return void
     */
    @Override
    public void configure(IMServerConfig serverConfig) {
        log.info("开始配置内部客户端...");
        initClient(serverConfig);
        afterPropertiesSet();
    }
}
