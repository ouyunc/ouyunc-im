package com.ouyunc.mq.kafka.properties;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import java.util.List;

/**
 * @Author fangzhenxun
 * @Description kafka 单例模式属性配置类（这里使用KafkaProperties 中的数据信息）
 * 如果需要更多的配置属行可以使用自定义的字段，
 * org.springframework.boot.autoconfigure.kafka.KafkaProperties kafka 的参数属行的配置
 * @Date 2020/3/13 13:28
 **/

public class StandaloneKafkaMqProperties extends KafkaProperties {


    /**
     * 生产属性设置
     **/
    private Producer producer;

    /**
     * 消费者属性设置
     **/
    private Consumer consumer;

    /**
     * 消费监听器属性设置
     **/
    private Listener listener;

    public Producer getProducer() {
        return producer;
    }

    public void setProducer(Producer producer) {
        this.producer = producer;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    public Listener getListener() {
        return listener;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public static class Producer {
        /**
         * procedure要求leader在考虑完成请求之前收到的确认数，用于控制发送记录在服务端的持久化，其值可以为如下：
         * #acks = 0 如果设置为零，则生产者将不会等待来自服务器的任何确认，该记录将立即添加到套接字缓冲区并视为已发送。在这种情况下，无法保证服务器已收到记录，并且重试配置将不会生效（因为客户端通常不会知道任何故障），为每条记录返回的偏移量始终设置为-1。
         * #acks = 1 这意味着leader会将记录写入其本地日志，但无需等待所有副本服务器的完全确认即可做出回应，在这种情况下，如果leader在确认记录后立即失败，但在将数据复制到所有的副本服务器之前，则记录将会丢失。
         * #acks = all 这意味着leader将等待完整的同步副本集以确认记录，这保证了只要至少一个同步副本服务器仍然存活，记录就不会丢失，这是最强有力的保证，这相当于acks = -1的设置。
         * #可以设置的值为：all, -1, 0, 1
         **/
        private String ack;

        /**
         * 每当多个记录被发送到同一分区时，生产者将尝试将记录一起批量处理为更少的请求，
         * 这有助于提升客户端和服务器上的性能，此配置控制默认批量大小（以字节为单位），默认值为16384
         **/
        private Integer batchSize;

        /**
         * 以逗号分隔的主机：端口对列表，用于建立与Kafka群集的初始连接
         **/
        private List<String> bootstrapServers;

        /**
         * 生产者可用于缓冲等待发送到服务器的记录的内存总字节数，默认值为33554432
         **/
        private Integer bufferMemory;

        /**
         * 在batch时候，吞吐量和延时性能 , 默认0
         **/
        private Integer lingerMs;

        /**
         * ID在发出请求时传递给服务器，用于服务器端日志记录
         **/
        private String clientId;

        /**
         * 生产者生成的所有数据的压缩类型，此配置接受标准压缩编解码器（'gzip'，'snappy'，'lz4'），
         * 它还接受'uncompressed'以及'producer'，分别表示没有压缩以及保留生产者设置的原始压缩编解码器，
         * 默认值为producer
         **/
        private String compressionType;

        /**
         * 如果该值大于零时，表示启用重试失败的发送次数
         **/
        private Integer retries;

        /**
         * key 和value 的Serializer类
         **/
        private Class<?> keySerializer = StringSerializer.class;
        private Class<?> valueSerializer = StringSerializer.class;


        public String getAck() {
            return ack;
        }

        public void setAck(String ack) {
            this.ack = ack;
        }

        public Integer getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(Integer batchSize) {
            this.batchSize = batchSize;
        }

        public List<String> getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(List<String> bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        public Integer getBufferMemory() {
            return bufferMemory;
        }

        public void setBufferMemory(Integer bufferMemory) {
            this.bufferMemory = bufferMemory;
        }

        public Integer getLingerMs() {
            return lingerMs;
        }

        public void setLingerMs(Integer lingerMs) {
            this.lingerMs = lingerMs;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getCompressionType() {
            return compressionType;
        }

        public void setCompressionType(String compressionType) {
            this.compressionType = compressionType;
        }

        public Integer getRetries() {
            return retries;
        }

        public void setRetries(Integer retries) {
            this.retries = retries;
        }

        public Class<?> getKeySerializer() {
            return keySerializer;
        }

        public void setKeySerializer(Class<?> keySerializer) {
            this.keySerializer = keySerializer;
        }

        public Class<?> getValueSerializer() {
            return valueSerializer;
        }

        public void setValueSerializer(Class<?> valueSerializer) {
            this.valueSerializer = valueSerializer;
        }
    }



    public static class Consumer {
        /**
         * 如果'enable.auto.commit'为true，则消费者偏移自动提交给Kafka的频率（以毫秒为单位），默认值为5000。
         **/
        private Integer autoCommitIntervalMs;

        /**
         * 当Kafka中没有初始偏移量或者服务器上不再存在当前偏移量时该怎么办，默认值为latest，表示自动将偏移重置为最新的偏移量
         * 可选的值为latest, earliest, none
         **/
        private String autoOffsetReset;

        /**
         * 以逗号分隔的主机：端口对列表，用于建立与Kafka群集的初始连接。
         **/
        private List<String> bootstrapServers;

        /**
         * ID在发出请求时传递给服务器;用于服务器端日志记录。
         **/
        private String clientId;

        /**
         * 如果为true，则消费者的偏移量将在后台定期提交，默认值为true
         **/
        private Boolean enableAutoCommit;

        /**
         * 如果没有足够的数据立即满足“fetch.min.bytes”给出的要求，服务器在回答获取请求之前将阻塞的最长时间（以毫秒为单位）
         * 默认值为500
         **/
        private Integer fetchMaxWait;

        /**
         * 服务器应以字节为单位返回获取请求的最小数据量，默认值为1，对应的kafka的参数为fetch.min.bytes
         **/
        private Integer fetchMinSize;

        /**
         * 用于标识此使用者所属的使用者组的唯一字符串
         **/
        private String groupId;

        /**
         * 心跳与消费者协调员之间的预期时间（以毫秒为单位），默认值为3000
         **/
        private Integer heartbeatInterval;

        /**
         * 一次调用poll()操作时返回的最大记录数，默认值为500
         **/
        private Integer maxPollRecords;

        /**
         * 手动提交设置与poll的心跳数,如果消息队列中没有消息，等待毫秒后，调用poll()方法。如果队列中有消息，立即消费消息，每次消费的消息的多少可以通过max.poll.records配置。
         **/
        private Integer maxPollIntervalMs;


        /**
         * 设置拉取数据的大小,15M
         **/
        private Integer maxPartitionFetchBytes;

        /**
         * 连接超时时间,20000
         **/
        private Integer sessionTimeoutMs;

        /**
         * 密钥的反序列化器类，实现类实现了接口org.apache.kafka.common.serialization.Deserializer
         **/
        private Class<?> keyDeserializer = StringDeserializer.class;

        /**
         * 值的反序列化器类，实现类实现了接口org.apache.kafka.common.serialization.Deserializer
         **/
        private Class<?> valueDeserializer = StringDeserializer.class;

        public Integer getAutoCommitIntervalMs() {
            return autoCommitIntervalMs;
        }

        public void setAutoCommitIntervalMs(Integer autoCommitIntervalMs) {
            this.autoCommitIntervalMs = autoCommitIntervalMs;
        }

        public String getAutoOffsetReset() {
            return autoOffsetReset;
        }

        public void setAutoOffsetReset(String autoOffsetReset) {
            this.autoOffsetReset = autoOffsetReset;
        }

        public List<String> getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(List<String> bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public Boolean getEnableAutoCommit() {
            return enableAutoCommit;
        }

        public void setEnableAutoCommit(Boolean enableAutoCommit) {
            this.enableAutoCommit = enableAutoCommit;
        }

        public Integer getFetchMaxWait() {
            return fetchMaxWait;
        }

        public void setFetchMaxWait(Integer fetchMaxWait) {
            this.fetchMaxWait = fetchMaxWait;
        }

        public Integer getFetchMinSize() {
            return fetchMinSize;
        }

        public void setFetchMinSize(Integer fetchMinSize) {
            this.fetchMinSize = fetchMinSize;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public Integer getHeartbeatInterval() {
            return heartbeatInterval;
        }

        public void setHeartbeatInterval(Integer heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
        }

        public Integer getMaxPollRecords() {
            return maxPollRecords;
        }

        public void setMaxPollRecords(Integer maxPollRecords) {
            this.maxPollRecords = maxPollRecords;
        }

        public Integer getMaxPollIntervalMs() {
            return maxPollIntervalMs;
        }

        public void setMaxPollIntervalMs(Integer maxPollIntervalMs) {
            this.maxPollIntervalMs = maxPollIntervalMs;
        }

        public Integer getMaxPartitionFetchBytes() {
            return maxPartitionFetchBytes;
        }

        public void setMaxPartitionFetchBytes(Integer maxPartitionFetchBytes) {
            this.maxPartitionFetchBytes = maxPartitionFetchBytes;
        }

        public Integer getSessionTimeoutMs() {
            return sessionTimeoutMs;
        }

        public void setSessionTimeoutMs(Integer sessionTimeoutMs) {
            this.sessionTimeoutMs = sessionTimeoutMs;
        }

        public Class<?> getKeyDeserializer() {
            return keyDeserializer;
        }

        public void setKeyDeserializer(Class<?> keyDeserializer) {
            this.keyDeserializer = keyDeserializer;
        }

        public Class<?> getValueDeserializer() {
            return valueDeserializer;
        }

        public void setValueDeserializer(Class<?> valueDeserializer) {
            this.valueDeserializer = valueDeserializer;
        }
    }




    public static class Listener {
        /**
         * 是否开启批量消费，true表示批量消费
         **/
        private Boolean batchListener;

        /**
         * 手动确认还是自动确认，  （当enable.auto.commit的值设置为false时，该值会生效；为true时不会生效）
         **/
        private String ackMode;

        /**
         * 客户端id
         **/
        private String clientId;

        /**
         * 在侦听器容器中运行的线程数（消费者线程数）
         **/
        private Integer concurrency;

        /**
         * 轮询消费者时使用的超时（以毫秒为单位）
         **/
        private Long pollTimeout;

        /**
         *  当ackMode为“COUNT”或“COUNT_TIME”时，偏移提交之间的记录数
         **/
        private Integer ackCount;

        /**
         *  当ackMode为“TIME”或“COUNT_TIME”时，偏移提交之间的时间（以毫秒为单位）
         **/
        private Long ackTime;
        private Long idleEventInterval;
        private Long monitorInterval;
        private Boolean logContainerConfig;
        private Float noPollThreshold;


        public Boolean getBatchListener() {
            return batchListener;
        }

        public void setBatchListener(Boolean batchListener) {
            this.batchListener = batchListener;
        }

        public String getAckMode() {
            return ackMode;
        }

        public void setAckMode(String ackMode) {
            this.ackMode = ackMode;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public Integer getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(Integer concurrency) {
            this.concurrency = concurrency;
        }

        public Long getPollTimeout() {
            return pollTimeout;
        }

        public void setPollTimeout(Long pollTimeout) {
            this.pollTimeout = pollTimeout;
        }

        public Integer getAckCount() {
            return ackCount;
        }

        public void setAckCount(Integer ackCount) {
            this.ackCount = ackCount;
        }

        public Long getAckTime() {
            return ackTime;
        }

        public void setAckTime(Long ackTime) {
            this.ackTime = ackTime;
        }

        public Long getIdleEventInterval() {
            return idleEventInterval;
        }

        public void setIdleEventInterval(Long idleEventInterval) {
            this.idleEventInterval = idleEventInterval;
        }

        public Long getMonitorInterval() {
            return monitorInterval;
        }

        public void setMonitorInterval(Long monitorInterval) {
            this.monitorInterval = monitorInterval;
        }

        public Boolean getLogContainerConfig() {
            return logContainerConfig;
        }

        public void setLogContainerConfig(Boolean logContainerConfig) {
            this.logContainerConfig = logContainerConfig;
        }

        public Float getNoPollThreshold() {
            return noPollThreshold;
        }

        public void setNoPollThreshold(Float noPollThreshold) {
            this.noPollThreshold = noPollThreshold;
        }
    }

}
