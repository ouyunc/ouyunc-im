package com.ouyunc.mq.kafka.builder;

import com.ouyunc.mq.kafka.strategy.KafkaStrategy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * @author fzx
 * @version 1.0
 * @description: kafka template builder
 * @date 2025/1/13 16:48
 */
public class KafkaTemplateBuilder extends AbstractKafkaBuilder<KafkaTemplate<?,?>>{

    /**
     * @description: kafka 模版构建器
     * @author fzx
     * @date 2025/1/13 16:50
     * @version 1.0
     */
    @Override
    public KafkaTemplate<?, ?> build() {
        //获取当前选中的配置策略
        KafkaStrategy<?,?> kafkaMqStrategy = currentKafkaStrategy();
        //构建生产者工厂
        ProducerFactory<?,?> producerFactory = kafkaMqStrategy.buildProducerFactory(kafkaProperties);
        //创建kafka操作模版
        return new KafkaTemplate<>(producerFactory);
    }
}
