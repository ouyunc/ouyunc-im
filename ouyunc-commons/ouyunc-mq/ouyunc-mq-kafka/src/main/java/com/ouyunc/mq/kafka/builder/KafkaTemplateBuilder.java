package com.ouyunc.mq.kafka.builder;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

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
        //构建生产者工厂
        ProducerFactory<?,?> producerFactory = buildProducerFactory();
        //创建kafka操作模版
        return new KafkaTemplate<>(producerFactory);
    }


    /**
     * @Author fangzhenxun
     * @Description  构建生产者工厂，将该方法装配成bean,交给spring来管理
     * @param
     * @return org.springframework.kafka.core.ProducerFactory
     **/
    public ProducerFactory<?,?> buildProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerProperties());
    }



    /**
     * @Author fangzhenxun
     * @Description  kafka 生产者的属性配置
     * @param
     * @return java.util.Map<java.lang.String,java.lang.Object>
     **/
    private Map<String, Object> producerProperties(){
        Map<String, Object> producerPropertiesMap = new HashMap<>(9);
        //kafka 地址,多个使用逗号隔开
        producerPropertiesMap.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        if (CollectionUtils.isNotEmpty(kafkaProperties.getProducer().getBootstrapServers())) {
            producerPropertiesMap.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getProducer().getBootstrapServers());
        }
        //消息确认应答模式
        producerPropertiesMap.put(ProducerConfig.ACKS_CONFIG, kafkaProperties.getProducer().getAck());
        //批量发送的消息数量
        producerPropertiesMap.put(ProducerConfig.BATCH_SIZE_CONFIG, kafkaProperties.getProducer().getBatchSize());
        //32M批处理缓冲区
        producerPropertiesMap.put(ProducerConfig.BUFFER_MEMORY_CONFIG, kafkaProperties.getProducer().getBufferMemory());
        //发送失败后的重复发送次数
        producerPropertiesMap.put(ProducerConfig.RETRIES_CONFIG, kafkaProperties.getProducer().getRetries());
        //linger.ms设置(吞吐量和延时性能)producer是按照batch进行发送的，但是还要看linger.ms的值，默认是0，表示不做停留。这种情况下，可能有的batch中没有包含足够多的produce请求就被发送出去了，造成了大量的小batch，给网络IO带来的极大的压力
        producerPropertiesMap.put(ProducerConfig.LINGER_MS_CONFIG, kafkaProperties.getProducer().getLingerMs());

        //#指定消息key和消息体的编解码方式
        producerPropertiesMap.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, kafkaProperties.getProducer().getKeySerializer());
        producerPropertiesMap.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, kafkaProperties.getProducer().getValueSerializer());
        return producerPropertiesMap;
    }

}
