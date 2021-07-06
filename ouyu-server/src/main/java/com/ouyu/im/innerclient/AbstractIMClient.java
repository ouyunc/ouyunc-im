package com.ouyu.im.innerclient;

import com.ouyu.im.config.IMServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description:
 * @Version V1.0
 **/
public abstract class AbstractIMClient implements IMClient{
    private static Logger log = LoggerFactory.getLogger(AbstractIMClient.class);


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
    public void configure(IMServerConfig serverConfig) {
        initClient(serverConfig);
        afterPropertiesSet();
    }
}
