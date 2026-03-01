package com.ouyunc.mq.rocket.builder;

import com.ouyunc.mq.rocket.properties.RocketProperties;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.remoting.protocol.heartbeat.MessageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @description: RocketMQ 消费者构建器（构建已配置的 PushConsumer，调用方需自行 subscribe、registerMessageListener、start）
 * @author fzx
 * @version 1.0
 */
public class RocketConsumerBuilder extends AbstractRocketBuilder<DefaultMQPushConsumer> {

    private static final Logger log = LoggerFactory.getLogger(RocketConsumerBuilder.class);

    @Override
    public DefaultMQPushConsumer build() {
        RocketProperties.Consumer consumerConfig = rocketProperties.getConsumer();
        if (consumerConfig == null) {
            throw new RuntimeException("RocketMQ 消费者配置不能为空");
        }
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerConfig.getConsumerGroup());
        String nameServers = resolveNameServers(consumerConfig.getNameServersStr(), rocketProperties.getNameServersStr());
        if (nameServers == null || nameServers.isEmpty()) {
            throw new RuntimeException("RocketMQ Name Server 地址不能为空");
        }
        consumer.setNamesrvAddr(nameServers);
        if (consumerConfig.getConsumeThreadMin() != null) {
            consumer.setConsumeThreadMin(consumerConfig.getConsumeThreadMin());
        }
        if (consumerConfig.getConsumeThreadMax() != null) {
            consumer.setConsumeThreadMax(consumerConfig.getConsumeThreadMax());
        }
        if (consumerConfig.getMessageModel() != null) {
            consumer.setMessageModel(MessageModel.valueOf(consumerConfig.getMessageModel()));
        }
        if (consumerConfig.getConsumeFromWhere() != null) {
            consumer.setConsumeFromWhere(ConsumeFromWhere.valueOf(consumerConfig.getConsumeFromWhere()));
        }
        if (consumerConfig.getConsumeMessageBatchMaxSize() != null) {
            consumer.setConsumeMessageBatchMaxSize(consumerConfig.getConsumeMessageBatchMaxSize());
        }
        return consumer;
    }

    private static String resolveNameServers(String consumerNameServers, String globalNameServers) {
        if (consumerNameServers != null && !consumerNameServers.isEmpty()) {
            return consumerNameServers;
        }
        return globalNameServers;
    }
}
