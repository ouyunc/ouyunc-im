package com.ouyunc.repository;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.MqttTopicSubscriptionOption;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.cache.config.CacheFactory;
import io.netty.handler.codec.mqtt.MqttMessage;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;

import java.util.List;
import java.util.Objects;

/**
 * mqtt 消息持久化操作, 单例模式
 */
public enum MqttRepository implements Repository{
    INSTANCE;

    private static final RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();

    /**
     * 保存全量信息
     * @param packet
     */
    @Override
    public void save(Packet packet) {
        // 直接保存到数据库中，或者influxdb等时序数据库中

    }

    @Override
    public boolean checkDup(Packet packet) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        Double score = redisTemplate.opsForZSet().score(CacheConstant.OUYUNC + CacheConstant.OFFLINE + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + message.getTo(), packet.getPacketId());
        // 如果分数不为 null，则表示值存在
        return !Objects.isNull(score);
    }


    /**
     * 保存遗嘱消息
     * @param mqttMessage
     */
    public void savePublishMessage(MqttMessage mqttMessage) {

    }

    /**
     * 取消订阅
     * @param topicFilterList
     * @param comboIdentity
     */
    @SuppressWarnings("unchecked")
    public void unSubscribe(String appKey, String comboIdentity, List<String> topicFilterList) {
        if (CollectionUtils.isEmpty(topicFilterList)) {
            return;
        }
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                // 删除订阅关系
                topicFilterList.forEach(topicFilter -> operations.opsForHash().delete((K) (CacheConstant.OUYUNC +  CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.MQTT + CacheConstant.TOPIC + topicFilter),  comboIdentity));
                return null;
            }
        });
    }
    /**
     * 订阅 主题
     * @param topicSubscriptionOptionList
     * @param comboIdentity
     */
    @SuppressWarnings("unchecked")
    public void subscribe(String appKey, String comboIdentity,  List<MqttTopicSubscriptionOption> topicSubscriptionOptionList) {
        if (CollectionUtils.isEmpty(topicSubscriptionOptionList)) {
            return;
        }
        String[] topicFilterArray = topicSubscriptionOptionList.parallelStream().map(MqttTopicSubscriptionOption::getTopicFilter).toArray(String[]::new);
        redisTemplate.opsForSet().add(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.MQTT + CacheConstant.TOPIC_LIST, topicFilterArray);
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                // 保存订阅关系
                topicSubscriptionOptionList.forEach(topicSubscriptionOption -> operations.opsForHash().putIfAbsent((K) (CacheConstant.OUYUNC +  CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.MQTT + CacheConstant.TOPIC + topicSubscriptionOption.getTopicFilter()),  comboIdentity, topicSubscriptionOption));
                return null;
            }
        });
    }
}
