package com.ouyunc.mq.kafka.builder;

import com.ouyunc.mq.kafka.strategy.KafkaStrategy;
import org.springframework.kafka.config.KafkaListenerContainerFactory;

/**
 * @author fzx
 * @version 1.0
 * @description: kafka 监听容器构建器
 * @date 2025/1/14 9:46
 */
public class KafkaListenerContainerFactoryBuilder extends AbstractKafkaBuilder<KafkaListenerContainerFactory<?>>{


    /**
     * @description: kafka 监听容器构建器
     * @author fzx
     * @date 2025/1/14 9:47
     * @version 1.0
     */
    @Override
    public KafkaListenerContainerFactory<?> build() {
        //获取当前选中的配置策略
        KafkaStrategy<?,?> kafkaMqStrategy = currentKafkaStrategy();
        //构建kafka监听容器工厂
        return kafkaMqStrategy.buildKafkaListenerContainerFactory(kafkaProperties);
    }
}
