package com.ouyunc.im.router;

import com.ouyunc.im.domain.MqttTopic;

import java.util.List;

/**
 * @Author fangzhenxun
 * @Description: 抽象消息路由策略
 **/
public abstract class AbstractTopicRouterStrategy implements RouterStrategy {


    /**
     * @param
     * @return io.netty.channel.pool.ChannelPool
     * @Author fangzhenxun
     * @Description 查找并返回可用的topic
     */
    public abstract List<MqttTopic> route(String topic);
}
