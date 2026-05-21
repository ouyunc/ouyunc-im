package com.ouyunc.repository;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.model.MqttTopicSubscriptionOption;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.mq.kafka.KafkaFactory;
import com.ouyunc.repository.support.QosIdempotencyHelper;
import io.netty.handler.codec.mqtt.MqttMessage;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * mqtt 消息持久化操作, 单例模式
 */
public enum MqttRepository implements Repository{
    INSTANCE;

    private static final RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();

    private static final StringRedisTemplate stringRedisTemplate = CacheFactory.STRING_REDIS.instance();
    /**
     * kafkaTemplate
     */
    private static final KafkaTemplate<String, Object> kafkaTemplate = KafkaFactory.KAFKA_TEMPLATE.instance();

    /**
     * 保存全量信息
     * @param packet
     */
    @Override
    public Future<?> save(Packet packet) {
        // 直接保存到数据库中，或者influxdb等时序数据库中
        Map<String, Object> headers = new HashMap<>();
        headers.put(KafkaHeaders.CORRELATION_ID, packet.getPacketId());
        // 这里是mqtt 的保存，如果想让业务分开，不同的业务可以使用不同的topic来区分
        return kafkaTemplate.send(MqConstant.KAFKA_SAVE_MESSAGE_TOPIC, MessageBuilder.withPayload(JSON.toJSONString(packet)).copyHeadersIfAbsent(headers).build());
    }

    @Override
    public boolean checkDup(Packet packet, String channelLoginIdentity) {
        return QosIdempotencyHelper.isDuplicate(redisTemplate, packet, channelLoginIdentity);
    }


    /**
     * 保存遗嘱消息
     * @param mqttMessage
     */
    public void savePublishMessage(MqttMessage mqttMessage) {

    }

    public void subscribe(String appKey, String comboIdentity, List<MqttTopicSubscriptionOption> list) {
        String topicListKey = CacheConstant.buildMqttTopicListCacheKey(appKey);
        String[] topicFilterArray = list.stream()
                .map(MqttTopicSubscriptionOption::getTopicFilter)
                .toArray(String[]::new);

        // 全部在同一 Pipeline 中执行，保证原子性
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> ops) throws DataAccessException {
                ops.opsForSet().add((K) topicListKey, (V[]) topicFilterArray);
                for (MqttTopicSubscriptionOption opt : list) {
                    String topicKey = CacheConstant.buildMqttTopicFilterCacheKey(appKey, opt.getTopicFilter());
                    ops.opsForHash().putIfAbsent((K) topicKey, comboIdentity, opt.getQos());
                }
                return null;
            }
        });
    }

    public void unSubscribe(String appKey, String comboIdentity, List<String> topicFilters) {
        String topicListKey = CacheConstant.buildMqttTopicListCacheKey(appKey);
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> ops) throws DataAccessException {
                for (String topicFilter : topicFilters) {
                    String topicKey = CacheConstant.buildMqttTopicFilterCacheKey(appKey, topicFilter);
                    ops.opsForHash().delete((K) topicKey, comboIdentity);
                    // 删后检查：若该 topic 无订阅者则从全局 Set 移除
                    // 注意：Pipeline 内无法读取结果做条件判断，改为后置清理
                }
                return null;
            }
        });
        // 后置清理无订阅者的 topic（非事务，最终一致即可）
        for (String topicFilter : topicFilters) {
            String topicKey = CacheConstant.buildMqttTopicFilterCacheKey(appKey, topicFilter);
            Long size = redisTemplate.opsForHash().size(topicKey);
            if (size != null && size == 0) {
                redisTemplate.opsForSet().remove(topicListKey, topicFilter);
                redisTemplate.delete(topicKey);
            }
        }
    }

}
