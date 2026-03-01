package com.ouyunc.mq.rocket;

import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.mq.rocket.builder.AbstractRocketBuilder;
import com.ouyunc.mq.rocket.builder.RocketConsumerBuilder;
import com.ouyunc.mq.rocket.builder.RocketProducerBuilder;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @description: RocketMQ 工厂（与 KafkaFactory 设计风格一致，枚举 + 懒加载单例）
 * @author fzx
 * @version 1.0
 */
public enum RocketFactory {

    ROCKET_PRODUCER(NumberConstant.NUMBER_3, "DefaultMQProducer v3 版本") {

        private static volatile DefaultMQProducer defaultMQProducer;

        @Override
        public DefaultMQProducer instance() {
            if (defaultMQProducer == null) {
                synchronized (RocketFactory.class) {
                    if (defaultMQProducer == null) {
                        AbstractRocketBuilder<DefaultMQProducer> builder = new RocketProducerBuilder();
                        defaultMQProducer = builder.build();
                    }
                }
            }
            return defaultMQProducer;
        }
    },

    ROCKET_CONSUMER_BUILDER(NumberConstant.NUMBER_3, "RocketConsumerBuilder v3 版本，用于创建配置好的 DefaultMQPushConsumer") {

        private static volatile RocketConsumerBuilder consumerBuilder;

        @Override
        public RocketConsumerBuilder instance() {
            if (consumerBuilder == null) {
                synchronized (RocketFactory.class) {
                    if (consumerBuilder == null) {
                        consumerBuilder = new RocketConsumerBuilder();
                    }
                }
            }
            return consumerBuilder;
        }
    };

    private final int version;
    private final String description;

    RocketFactory(int version, String description) {
        this.version = version;
        this.description = description;
    }

    public int getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    private static final Logger log = LoggerFactory.getLogger(RocketFactory.class);

    /**
     * 获取实例：PRODUCER 返回 DefaultMQProducer 单例，CONSUMER_BUILDER 返回 RocketConsumerBuilder 单例（再调用 build() 得到 DefaultMQPushConsumer）
     */
    public abstract <T> T instance();
}
