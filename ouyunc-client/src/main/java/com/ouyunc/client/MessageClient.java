package com.ouyunc.client;


import com.ouyunc.core.properties.MessageProperties;

/**
 * @Author fzx
 * @Description: 客户端
 **/
public interface MessageClient {

    /**
     * @Author fzx
     * @Description 配置客户端,
     */
    void configure(MessageProperties messageProperties);

    /**
     * @Author fzx
     * @Description 停止内部客户端
     */

    void stop();
}
