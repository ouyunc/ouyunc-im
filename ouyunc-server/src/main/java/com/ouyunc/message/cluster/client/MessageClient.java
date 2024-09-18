package com.ouyunc.message.cluster.client;


import com.ouyunc.message.properties.MessageServerProperties;

/**
 * @Author fzx
 * @Description: 内置客户端，在集群中使用，这个接口是入口
 **/
public interface MessageClient {

    /**
     * @Author fzx
     * @Description 配置内置客户端,用作集群之间的通信（消息路由）
     */
    void configure(MessageServerProperties messageServerProperties);

    /**
     * @Author fzx
     * @Description 停止内部客户端
     */

    void stop();
}
