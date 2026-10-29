package com.ouyunc.mq.rocket.builder;

import com.ouyunc.mq.rocket.properties.RocketProperties;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.remoting.RPCHook;
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
        RocketProperties rocket = properties();
        RocketProperties.Producer producerConfig = rocket.getProducer();
        if (producerConfig == null) {
            throw new RuntimeException("RocketMQ 生产者配置不能为空");
        }
        String nameServers = resolveNameServers(producerConfig.getNameServersStr(), rocket.getNameServersStr());
        if (nameServers == null || nameServers.isEmpty()) {
            throw new RuntimeException("RocketMQ Name Server 地址不能为空");
        }
        RPCHook aclHook = RocketClientSupport.aclHook(rocket);
        DefaultMQProducer producer = aclHook == null
                ? new DefaultMQProducer(producerConfig.getProducerGroup())
                : new DefaultMQProducer(producerConfig.getProducerGroup(), aclHook);
        RocketClientSupport.applyCommon(producer, rocket, nameServers);
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
        if (producerConfig.getRetryNextServer() != null) {
            producer.setRetryAnotherBrokerWhenNotStoreOK(producerConfig.getRetryNextServer());
        }
        log.info("RocketMQ Producer 准备启动, group={}, namesrv={}, acl={}, vipChannel={}",
                producer.getProducerGroup(), nameServers, aclHook != null, producer.isVipChannelEnabled());
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
