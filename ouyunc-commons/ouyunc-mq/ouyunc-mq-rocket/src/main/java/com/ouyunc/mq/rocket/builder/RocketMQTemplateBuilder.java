package com.ouyunc.mq.rocket.builder;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.InitializingBean;

/**
 * RocketMQTemplate 构建器（非 Spring 环境下使用，与 KafkaTemplateBuilder 一致）。
 * 从 ouyunc.mq.rocket 配置构建 {@link org.apache.rocketmq.spring.core.RocketMQTemplate}，
 * 通过 {@link com.ouyunc.mq.rocket.RocketFactory#ROCKET_TEMPLATE}.instance() 获取单例。
 * <p>
 * 已设置项：
 * <ul>
 *   <li><b>producer</b>（必选）：用于 syncSend/asyncSend/sendOneWay 等发送，由 RocketProducerBuilder 从配置构建</li>
 *   <li><b>charset</b>（可选）：若配置了 ouyunc.mq.rocket.charset 则设置，否则使用模板默认 UTF-8</li>
 *   <li><b>afterPropertiesSet</b>：调用以初始化模板内部 MessageConverter 等，并执行 producer.start()</li>
 * </ul>
 * 未设置项（按需使用）：
 * <ul>
 *   <li><b>consumer</b>：仅在使用 {@code RocketMQTemplate.receive()} 拉取消息时需要，
 *   可自行构建 DefaultLitePullConsumer 并 {@code template.setConsumer(consumer)}</li>
 *   <li><b>messageQueueSelector</b>：顺序发送时选择队列，默认已为 SelectMessageQueueByHash，一般无需改</li>
 * </ul>
 * </p>
 *
 * @author fzx
 * @version 1.0
 */
public class RocketMQTemplateBuilder extends AbstractRocketBuilder<RocketMQTemplate> {

    @Override
    public RocketMQTemplate build() {
        RocketProducerBuilder producerBuilder = new RocketProducerBuilder();
        DefaultMQProducer producer = producerBuilder.build();
        RocketMQTemplate template = new RocketMQTemplate();
        template.setProducer(producer);
        if (rocketProperties != null && rocketProperties.getCharset() != null && !rocketProperties.getCharset().isEmpty()) {
            template.setCharset(rocketProperties.getCharset());
        }
        try {
            template.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException("RocketMQTemplate afterPropertiesSet 失败", e);
        }
        return template;
    }
}
