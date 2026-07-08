package com.ouyunc.mq.rocket.properties;

import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @description: RocketMQ 属性配置
 * @author fzx
 * @version 1.0
 */
public class RocketProperties {

    /**
     * Name Server 地址列表，多个用逗号或分号隔开，与 Kafka bootstrapServers 风格一致
     */
    private final List<String> nameServers = new ArrayList<>();

    /**
     * 生产者属性
     */
    private Producer producer;

    /**
     * 消费者属性
     */
    private Consumer consumer;

    /**
     * 消息字符集（可选），用于 RocketMQTemplate 序列化/反序列化，默认不设置时模板使用 UTF-8
     */
    private String charset;

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public List<String> getNameServers() {
        return nameServers;
    }

    public void setNameServers(List<String> nameServers) {
        if (CollectionUtils.isNotEmpty(nameServers)) {
            this.nameServers.clear();
            for (String ns : nameServers) {
                for (String item : ns.split("[,;]")) {
                    String trimmed = item.trim();
                    if (!trimmed.isEmpty()) {
                        this.nameServers.add(trimmed);
                    }
                }
            }
        }
    }

    /**
     * 获取 namesrv 地址字符串，多个用分号连接（RocketMQ 标准格式）
     */
    public String getNameServersStr() {
        if (CollectionUtils.isEmpty(nameServers)) {
            return "";
        }
        return String.join(";", nameServers);
    }

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

    /**
     * 生产者内部静态类
     */
    public static class Producer {
        /**
         * 生产者组名
         */
        private String producerGroup = "DEFAULT_PRODUCER_GROUP";

        /**
         * 发送消息超时时间，毫秒，默认 3000
         */
        private Integer sendMsgTimeout = 3000;

        /**
         * 消息体超过该值则压缩，字节，默认 4096
         */
        private Integer compressMsgBodyOverHowmuch = 4096;

        /**
         * 同步发送失败重试次数，默认 2
         */
        private Integer retryTimesWhenSendFailed = 2;

        /**
         * 异步发送失败重试次数，默认 2
         */
        private Integer retryTimesWhenSendAsyncFailed = 2;

        /**
         * 是否在发送失败时尝试另一台 broker，默认 true
         */
        private Boolean retryNextServer = true;

        /**
         * 最大消息体大小，字节，默认 4194304 (4M)
         */
        private Integer maxMessageSize = 4194304;

        /**
         * Name Server 地址（可覆盖全局），多个用分号隔开
         */
        private final List<String> nameServers = new ArrayList<>();

        public String getProducerGroup() {
            return producerGroup;
        }

        public void setProducerGroup(String producerGroup) {
            this.producerGroup = producerGroup;
        }

        public Integer getSendMsgTimeout() {
            return sendMsgTimeout;
        }

        public void setSendMsgTimeout(Integer sendMsgTimeout) {
            this.sendMsgTimeout = sendMsgTimeout;
        }

        public Integer getCompressMsgBodyOverHowmuch() {
            return compressMsgBodyOverHowmuch;
        }

        public void setCompressMsgBodyOverHowmuch(Integer compressMsgBodyOverHowmuch) {
            this.compressMsgBodyOverHowmuch = compressMsgBodyOverHowmuch;
        }

        public Integer getRetryTimesWhenSendFailed() {
            return retryTimesWhenSendFailed;
        }

        public void setRetryTimesWhenSendFailed(Integer retryTimesWhenSendFailed) {
            this.retryTimesWhenSendFailed = retryTimesWhenSendFailed;
        }

        public Integer getRetryTimesWhenSendAsyncFailed() {
            return retryTimesWhenSendAsyncFailed;
        }

        public void setRetryTimesWhenSendAsyncFailed(Integer retryTimesWhenSendAsyncFailed) {
            this.retryTimesWhenSendAsyncFailed = retryTimesWhenSendAsyncFailed;
        }

        public Boolean getRetryNextServer() {
            return retryNextServer;
        }

        public void setRetryNextServer(Boolean retryNextServer) {
            this.retryNextServer = retryNextServer;
        }

        public Integer getMaxMessageSize() {
            return maxMessageSize;
        }

        public void setMaxMessageSize(Integer maxMessageSize) {
            this.maxMessageSize = maxMessageSize;
        }

        public List<String> getNameServers() {
            return nameServers;
        }

        public void setNameServers(List<String> nameServers) {
            if (CollectionUtils.isNotEmpty(nameServers)) {
                this.nameServers.clear();
                for (String ns : nameServers) {
                    for (String item : ns.split("[,;]")) {
                        String trimmed = item.trim();
                        if (!trimmed.isEmpty()) {
                            this.nameServers.add(trimmed);
                        }
                    }
                }
            }
        }

        public String getNameServersStr() {
            if (CollectionUtils.isEmpty(nameServers)) {
                return null;
            }
            return String.join(";", nameServers);
        }
    }

    /**
     * 消费者内部静态类
     */
    public static class Consumer {
        /**
         * 消费者组名
         */
        private String consumerGroup = "DEFAULT_CONSUMER_GROUP";

        /**
         * 消费线程池最小线程数，默认 20
         */
        private Integer consumeThreadMin = 20;

        /**
         * 消费线程池最大线程数，默认 64
         */
        private Integer consumeThreadMax = 64;

        /**
         * 消息模式：CLUSTERING 集群，BROADCASTING 广播，默认 CLUSTERING
         */
        private String messageModel = "CLUSTERING";

        /**
         * 从哪里开始消费：CONSUME_FROM_LAST_OFFSET, CONSUME_FROM_FIRST_OFFSET, CONSUME_FROM_TIMESTAMP，默认 CONSUME_FROM_LAST_OFFSET
         */
        private String consumeFromWhere = "CONSUME_FROM_LAST_OFFSET";

        /**
         * 单次消费最大消息数，默认 1
         */
        private Integer consumeMessageBatchMaxSize = 1;

        /**
         * 拉取消息超时时间，毫秒，默认 10000
         */
        private Long pullTimeout = 10000L;

        /**
         * Name Server 地址（可覆盖全局）
         */
        private final List<String> nameServers = new ArrayList<>();

        public String getConsumerGroup() {
            return consumerGroup;
        }

        public void setConsumerGroup(String consumerGroup) {
            this.consumerGroup = consumerGroup;
        }

        public Integer getConsumeThreadMin() {
            return consumeThreadMin;
        }

        public void setConsumeThreadMin(Integer consumeThreadMin) {
            this.consumeThreadMin = consumeThreadMin;
        }

        public Integer getConsumeThreadMax() {
            return consumeThreadMax;
        }

        public void setConsumeThreadMax(Integer consumeThreadMax) {
            this.consumeThreadMax = consumeThreadMax;
        }

        public String getMessageModel() {
            return messageModel;
        }

        public void setMessageModel(String messageModel) {
            this.messageModel = messageModel;
        }

        public String getConsumeFromWhere() {
            return consumeFromWhere;
        }

        public void setConsumeFromWhere(String consumeFromWhere) {
            this.consumeFromWhere = consumeFromWhere;
        }

        public Integer getConsumeMessageBatchMaxSize() {
            return consumeMessageBatchMaxSize;
        }

        public void setConsumeMessageBatchMaxSize(Integer consumeMessageBatchMaxSize) {
            this.consumeMessageBatchMaxSize = consumeMessageBatchMaxSize;
        }

        public Long getPullTimeout() {
            return pullTimeout;
        }

        public void setPullTimeout(Long pullTimeout) {
            this.pullTimeout = pullTimeout;
        }

        public List<String> getNameServers() {
            return nameServers;
        }

        public void setNameServers(List<String> nameServers) {
            if (CollectionUtils.isNotEmpty(nameServers)) {
                this.nameServers.clear();
                for (String ns : nameServers) {
                    for (String item : ns.split("[,;]")) {
                        String trimmed = item.trim();
                        if (!trimmed.isEmpty()) {
                            this.nameServers.add(trimmed);
                        }
                    }
                }
            }
        }

        public String getNameServersStr() {
            if (CollectionUtils.isEmpty(nameServers)) {
                return null;
            }
            return String.join(";", nameServers);
        }
    }
}
