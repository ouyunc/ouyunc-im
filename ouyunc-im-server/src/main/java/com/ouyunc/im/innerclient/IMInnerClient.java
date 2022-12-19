package com.ouyunc.im.innerclient;

import com.ouyunc.im.config.IMServerConfig;

/**
 * @Author fangzhenxun
 * @Description: 内置客户端，在集群中使用，这个接口是入口
 * @Version V3.0
 **/
public interface IMInnerClient {

    /**
     * @Author fangzhenxun
     * @Description 配置内置客户端根据当前服务的配置注册到当前服务端以及其他已经启动的服务端上
     * @param serverConfig
     * @return void
     */
    void configure(IMServerConfig serverConfig);

    /**
     * @Author fangzhenxun
     * @Description 停止内部客户端
     * @return void
     */

    void stop();
}
