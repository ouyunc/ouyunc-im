package com.ouyunc.mq.kafka.strategy;


import com.ouyunc.mq.kafka.enums.KafkaModeEnum;
import com.ouyunc.mq.kafka.properties.ClusterKafkaMqProperties;
import com.ouyunc.mq.kafka.properties.KafkaProperties;
import com.ouyunc.mq.kafka.properties.StandaloneKafkaMqProperties;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author fangzhenxun
 * @Description 单例策略具体类
 **/

public class StandaloneKafkaMqStrategy implements KafkaMqStrategy {
    private static final Logger log = LoggerFactory.getLogger(StandaloneKafkaMqStrategy.class);

    /**
     * 单例模式kakfa的属性配置类
     **/

    private StandaloneKafkaMqProperties standaloneKafkaMqProperties;


    @Override
    public KafkaModeEnum getMode() {
        return KafkaModeEnum.STANDALONE;
    }

    /**
     * @Author fangzhenxun
     * @Description  构建生产者工厂，将该方法装配成bean,交给spring来管理
     * @param
     * @return org.springframework.kafka.core.ProducerFactory
     **/
    @Override
    public ProducerFactory<?,?> buildProducerFactory(KafkaProperties kafkaProperties) {
        if (kafkaProperties instanceof StandaloneKafkaMqProperties standaloneKafkaProperties) {
            this.standaloneKafkaMqProperties = standaloneKafkaProperties;
        }
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
        producerPropertiesMap.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, standaloneKafkaMqProperties.getBootstrapServers());
        if (CollectionUtils.isNotEmpty(standaloneKafkaMqProperties.getProducer().getBootstrapServers())) {
            producerPropertiesMap.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, standaloneKafkaMqProperties.getProducer().getBootstrapServers());
        }
        //消息确认应答模式
        producerPropertiesMap.put(ProducerConfig.ACKS_CONFIG, standaloneKafkaMqProperties.getProducer().getAck());
        //批量发送的消息数量
        producerPropertiesMap.put(ProducerConfig.BATCH_SIZE_CONFIG, standaloneKafkaMqProperties.getProducer().getBatchSize());
        //32M批处理缓冲区
        producerPropertiesMap.put(ProducerConfig.BUFFER_MEMORY_CONFIG, standaloneKafkaMqProperties.getProducer().getBufferMemory());
        //发送失败后的重复发送次数
        producerPropertiesMap.put(ProducerConfig.RETRIES_CONFIG, standaloneKafkaMqProperties.getProducer().getRetries());
        //linger.ms设置(吞吐量和延时性能)producer是按照batch进行发送的，但是还要看linger.ms的值，默认是0，表示不做停留。这种情况下，可能有的batch中没有包含足够多的produce请求就被发送出去了，造成了大量的小batch，给网络IO带来的极大的压力
        producerPropertiesMap.put(ProducerConfig.LINGER_MS_CONFIG, standaloneKafkaMqProperties.getProducer().getLingerMs());

        //#指定消息key和消息体的编解码方式
        producerPropertiesMap.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, standaloneKafkaMqProperties.getProducer().getKeySerializer());
        producerPropertiesMap.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, standaloneKafkaMqProperties.getProducer().getValueSerializer());
        return producerPropertiesMap;
    }




    /**
     * @Author fangzhenxun
     * @Description  kafka 消费者监听器，这里主要用于配置消费这的并发数等一些配置
     * @param
     * @return org.springframework.kafka.config.KafkaListenerContainerFactory<?>
     **/
    public KafkaListenerContainerFactory<?> multiListenerContainer() {
        ConcurrentKafkaListenerContainerFactory<String, String> ckcFactory = new ConcurrentKafkaListenerContainerFactory<>();
        //配置消费者工厂
        ckcFactory.setConsumerFactory(consumerFactory());
        //是否批量消费
        ckcFactory.setBatchListener(standaloneKafkaMqProperties.getListener().getBatchListener());
        //设置消费的线程数
        ckcFactory.setConcurrency(standaloneKafkaMqProperties.getListener().getConcurrency());
        //如果消息队列中没有消息，等待timeout毫秒后，调用poll()方法。
        // 如果队列中有消息，立即消费消息，每次消费的消息的多少可以通过max.poll.records配置。
        //手动提交无需配置
        ckcFactory.getContainerProperties().setPollTimeout(standaloneKafkaMqProperties.getListener().getPollTimeout());
        //设置提交偏移量的方式， MANUAL_IMMEDIATE 表示消费一条提交一次；MANUAL表示批量提交一次
        ckcFactory.getContainerProperties().setAckMode(ContainerProperties.AckMode.valueOf(standaloneKafkaMqProperties.getListener().getAckMode()));
        return ckcFactory;
    }


    /**
     * @Author fangzhenxun
     * @Description  装配消费者工厂
     * @param
     * @return org.springframework.kafka.core.ConsumerFactory<java.lang.String,java.lang.String>
     **/
    public ConsumerFactory<String, String> consumerFactory() {
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
        consumerPropertiesMap.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, standaloneKafkaMqProperties.getBootstrapServers());
        if (CollectionUtils.isNotEmpty(standaloneKafkaMqProperties.getConsumer().getBootstrapServers())) {
            consumerPropertiesMap.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, standaloneKafkaMqProperties.getConsumer().getBootstrapServers());
        }
        //消费者组id
        consumerPropertiesMap.put(ConsumerConfig.GROUP_ID_CONFIG, standaloneKafkaMqProperties.getConsumer().getGroupId());
        //是否开启自动提交
        consumerPropertiesMap.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, standaloneKafkaMqProperties.getConsumer().getEnableAutoCommit());
        //批量消费一次最大拉取的数据量
        consumerPropertiesMap.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, standaloneKafkaMqProperties.getConsumer().getMaxPollRecords());
        //最早未被消费的offset earliest
        consumerPropertiesMap.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, standaloneKafkaMqProperties.getConsumer().getAutoOffsetReset());
        //连接超时时间,20000
        consumerPropertiesMap.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, standaloneKafkaMqProperties.getConsumer().getSessionTimeoutMs());
        //消费者最大心跳时间间隔,默认300s   300000
        consumerPropertiesMap.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, standaloneKafkaMqProperties.getConsumer().getMaxPollIntervalMs());
        //设置拉取数据的大小,15M  15728640
        consumerPropertiesMap.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, standaloneKafkaMqProperties.getConsumer().getMaxPartitionFetchBytes());
        //自动提交的间隔时间
        consumerPropertiesMap.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, standaloneKafkaMqProperties.getConsumer().getAutoCommitIntervalMs());
        //指定消息key和消息体的编解码方式
        consumerPropertiesMap.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, standaloneKafkaMqProperties.getConsumer().getKeyDeserializer());
        consumerPropertiesMap.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, standaloneKafkaMqProperties.getConsumer().getValueDeserializer());
        return consumerPropertiesMap;
    }


}
