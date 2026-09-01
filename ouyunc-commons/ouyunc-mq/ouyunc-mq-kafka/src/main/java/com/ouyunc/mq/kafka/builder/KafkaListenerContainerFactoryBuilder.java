package com.ouyunc.mq.kafka.builder;

import com.ouyunc.mq.kafka.properties.KafkaProperties;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

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
        //构建kafka监听容器工厂
        return buildKafkaListenerContainerFactory();
    }



    /**
     * @Author fangzhenxun
     * @Description  kafka 消费者监听器，这里主要用于配置消费这的并发数等一些配置
     * @param
     * @return org.springframework.kafka.config.KafkaListenerContainerFactory<?>
     **/
    public KafkaListenerContainerFactory<?> buildKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> ckcFactory = new ConcurrentKafkaListenerContainerFactory<>();
        //配置消费者工厂
        ckcFactory.setConsumerFactory(consumerFactory());
        //是否批量消费
        ckcFactory.setBatchListener(kafkaProperties.getListener().getBatchListener());
        //设置消费的线程数
        ckcFactory.setConcurrency(kafkaProperties.getListener().getConcurrency());
        //如果消息队列中没有消息，等待timeout毫秒后，调用poll()方法。
        // 如果队列中有消息，立即消费消息，每次消费的消息的多少可以通过max.poll.records配置。
        //手动提交无需配置
        ckcFactory.getContainerProperties().setPollTimeout(kafkaProperties.getListener().getPollTimeout());
        //设置提交偏移量的方式， MANUAL_IMMEDIATE 表示消费一条提交一次；MANUAL表示批量提交一次
        ckcFactory.getContainerProperties().setAckMode(ContainerProperties.AckMode.valueOf(kafkaProperties.getListener().getAckMode()));
        return ckcFactory;
    }




    /**
     * @Author fangzhenxun
     * @Description  装配消费者工厂
     * @param
     * @return org.springframework.kafka.core.ConsumerFactory<java.lang.String,java.lang.String>
     **/

    private ConsumerFactory<String, String> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerProperties());
    }


    /**
     * @Author fangzhenxun
     * @Description   配置消费者属性参数
     * @param
     * @return java.util.Map<java.lang.String,java.lang.Object>
     **/
    private Map<String, Object> consumerProperties() {
        Map<String, Object> consumerPropertiesMap = new HashMap<>(11);
        //消费的服务地址
        consumerPropertiesMap.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        if (CollectionUtils.isNotEmpty(kafkaProperties.getConsumer().getBootstrapServers())) {
            consumerPropertiesMap.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getConsumer().getBootstrapServers());
        }
        //消费者组id
        consumerPropertiesMap.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getConsumer().getGroupId());
        //是否开启自动提交
        consumerPropertiesMap.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, kafkaProperties.getConsumer().getEnableAutoCommit());
        //批量消费一次最大拉取的数据量
        consumerPropertiesMap.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, kafkaProperties.getConsumer().getMaxPollRecords());
        //最早未被消费的offset earliest
        consumerPropertiesMap.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaProperties.getConsumer().getAutoOffsetReset());
        //连接超时时间,20000
        consumerPropertiesMap.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, kafkaProperties.getConsumer().getSessionTimeoutMs());
        //消费者最大心跳时间间隔,默认300s   300000
        consumerPropertiesMap.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, kafkaProperties.getConsumer().getMaxPollIntervalMs());
        //设置拉取数据的大小,15M  15728640
        consumerPropertiesMap.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, kafkaProperties.getConsumer().getMaxPartitionFetchBytes());
        //自动提交的间隔时间
        consumerPropertiesMap.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, kafkaProperties.getConsumer().getAutoCommitIntervalMs());
        //指定消息key和消息体的编解码方式
        consumerPropertiesMap.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, kafkaProperties.getConsumer().getKeyDeserializer());
        consumerPropertiesMap.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, kafkaProperties.getConsumer().getValueDeserializer());
        // extra 放在 typed 字段之后：可追加 SASL 等，也可按需覆盖同名项
        KafkaProperties.mergeClientProperties(consumerPropertiesMap, kafkaProperties.getProperties());
        if (kafkaProperties.getConsumer() != null) {
            KafkaProperties.mergeClientProperties(consumerPropertiesMap, kafkaProperties.getConsumer().getProperties());
        }
        return consumerPropertiesMap;
    }

}
