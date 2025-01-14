package com.ouyunc.mq.kafka.strategy;

import com.ouyunc.mq.kafka.enums.KafkaModeEnum;
import com.ouyunc.mq.kafka.properties.KafkaProperties;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ProducerFactory;

/**
 * @Author fangzhenxun
 * @Description kafka 策略抽象类
 * @Date 2020/3/13 11:10
 **/
public interface KafkaMqStrategy<K, V> {


    /**
     * 标识kafka实现类的模式类型
     */
    KafkaModeEnum getMode();

    /**
     * @Author fangzhenxun
     * @Description   生产者工厂
     * @Date 2020/3/13 11:20
     * @param
     * @return org.springframework.kafka.core.ProducerFactory<K,V>
     **/
    ProducerFactory<K, V> buildProducerFactory(KafkaProperties kafkaProperties);

    /**
     * @Author fangzhenxun
     * @Description   构建kafka 监听容器工厂
     * @Date 2020/3/13 11:20
     * @param
     * @return org.springframework.kafka.core.ProducerFactory<K,V>
     **/
    KafkaListenerContainerFactory<?> buildKafkaListenerContainerFactory(KafkaProperties kafkaProperties);
}
