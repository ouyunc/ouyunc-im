package com.ouyunc.mq.rocket;

import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.mq.rocket.builder.AbstractRocketBuilder;
import com.ouyunc.mq.rocket.builder.RocketConsumerBuilder;
import com.ouyunc.mq.rocket.builder.RocketMQTemplateBuilder;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RocketMQ 工厂（与 KafkaFactory 设计风格一致：枚举 + 懒加载单例，非 Spring 环境下使用）。
 * <p>
 * 发送消息：通过 {@link #ROCKET_TEMPLATE}.instance() 获取 {@link RocketMQTemplate} 单例（由 RocketMQTemplateBuilder 从 ouyunc.mq.rocket 配置构建）。
 * </p>
 * <p>
 * 消费消息：通过 {@link #ROCKET_CONSUMER_BUILDER}.instance().build() 得到 DefaultMQPushConsumer，或使用 @RocketMQMessageListener（需 Spring 环境）。
 * </p>
 *
 * @author fzx
 * @version 1.0
 */
public enum RocketFactory {

    ROCKET_TEMPLATE(NumberConstant.NUMBER_3, "RocketMQTemplate v3，syncSend/asyncSend/sendOneWay，与 KafkaTemplate 用法一致") {

        private static volatile RocketMQTemplate rocketMQTemplate;

        @Override
        public RocketMQTemplate instance() {
            if (rocketMQTemplate == null) {
                synchronized (RocketFactory.class) {
                    if (rocketMQTemplate == null) {
                        AbstractRocketBuilder<RocketMQTemplate> builder = new RocketMQTemplateBuilder();
                        rocketMQTemplate = builder.build();
                    }
                }
            }
            return rocketMQTemplate;
        }
    },

    ROCKET_CONSUMER_BUILDER(NumberConstant.NUMBER_3, "RocketConsumerBuilder v3，用于创建配置好的 DefaultMQPushConsumer") {

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
     * 获取实例：ROCKET_TEMPLATE 返回 RocketMQTemplate 单例；ROCKET_CONSUMER_BUILDER 返回 RocketConsumerBuilder 单例（再调用 build() 得到 DefaultMQPushConsumer）。
     */
    public abstract <T> T instance();
}
