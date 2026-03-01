package com.ouyunc.mq.rocket.builder;

import com.ouyunc.mq.rocket.properties.RocketProperties;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @description: RocketMQ 生产者构建器
 * @author fzx
 * @version 1.0
 */
public class RocketProducerBuilder extends AbstractRocketBuilder<DefaultMQProducer> {

    private static final Logger log = LoggerFactory.getLogger(RocketProducerBuilder.class);

    @Override
    public DefaultMQProducer build() {
        RocketProperties.Producer producerConfig = rocketProperties.getProducer();
        if (producerConfig == null) {
            throw new RuntimeException("RocketMQ 生产者配置不能为空");
        }
        DefaultMQProducer producer = new DefaultMQProducer(producerConfig.getProducerGroup());
        String nameServers = resolveNameServers(producerConfig.getNameServersStr(), rocketProperties.getNameServersStr());
        if (nameServers == null || nameServers.isEmpty()) {
            throw new RuntimeException("RocketMQ Name Server 地址不能为空");
        }
        producer.setNamesrvAddr(nameServers);
        if (producerConfig.getSendMsgTimeout() != null) {
            producer.setSendMsgTimeout(producerConfig.getSendMsgTimeout());
        }
        if (producerConfig.getCompressMsgBodyOverHowmuch() != null) {
            producer.setCompressMsgBodyOverHowmuch(producerConfig.getCompressMsgBodyOverHowmuch());
        }
        if (producerConfig.getRetryTimesWhenSendFailed() != null) {
            producer.setRetryTimesWhenSendFailed(producerConfig.getRetryTimesWhenSendFailed());
        }
        if (producerConfig.getRetryTimesWhenSendAsyncFailed() != null) {
            producer.setRetryTimesWhenSendAsyncFailed(producerConfig.getRetryTimesWhenSendAsyncFailed());
        }
        if (producerConfig.getMaxMessageSize() != null) {
            producer.setMaxMessageSize(producerConfig.getMaxMessageSize());
        }
        try {
            producer.start();
        } catch (Exception e) {
            log.error("RocketMQ Producer 启动失败", e);
            throw new RuntimeException("RocketMQ Producer 启动失败", e);
        }
        return producer;
    }

    private static String resolveNameServers(String producerNameServers, String globalNameServers) {
        if (producerNameServers != null && !producerNameServers.isEmpty()) {
            return producerNameServers;
        }
        return globalNameServers;
    }
}
