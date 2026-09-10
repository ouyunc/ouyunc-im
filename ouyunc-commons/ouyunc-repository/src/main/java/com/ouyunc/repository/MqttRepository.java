package com.ouyunc.repository;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.model.MqttTopicSubscriptionOption;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.MqttCodecUtil;
import com.ouyunc.base.utils.MqttTopicFilterUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.repository.support.QosIdempotencyHelper;
import com.ouyunc.repository.support.RepositorySupports;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttVersion;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * mqtt 消息持久化操作, 单例模式
 */
public enum MqttRepository implements Repository{
    INSTANCE;

    private static final RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();

    @Override
    public CompletableFuture<?> save(Packet packet) {
        return RepositorySupports.MQ.save(packet);
    }

    @Override
    public boolean checkDup(Packet packet, String channelLoginIdentity) {
        return QosIdempotencyHelper.isDuplicate(redisTemplate, packet, channelLoginIdentity);
    }


    private static final String RETAIN_FIELD_TOPIC = "topic";
    private static final String RETAIN_FIELD_QOS = "qos";
    private static final String RETAIN_FIELD_CONTENT = "content";

    /**
     * retain=1 时覆盖该 topic 的保留消息；payload 为空则删除（MQTT 规范）。
     */
    public void savePublishMessage(String appKey, MqttMessage mqttMessage) {
        if (StringUtils.isBlank(appKey) || !(mqttMessage instanceof MqttPublishMessage publish)) {
            return;
        }
        if (!publish.fixedHeader().isRetain()) {
            return;
        }
        String topic = publish.variableHeader().topicName();
        if (StringUtils.isBlank(topic)) {
            return;
        }
        String retainKey = CacheConstant.buildMqttRetainCacheKey(appKey, topic);
        String topicSetKey = CacheConstant.buildMqttRetainTopicSetCacheKey(appKey);
        int readable = publish.payload() == null ? 0 : publish.payload().readableBytes();
        if (readable == 0) {
            redisTemplate.delete(retainKey);
            redisTemplate.opsForSet().remove(topicSetKey, topic);
            return;
        }
        Map<String, String> fields = new HashMap<>();
        fields.put(RETAIN_FIELD_TOPIC, topic);
        fields.put(RETAIN_FIELD_QOS, String.valueOf(publish.fixedHeader().qosLevel().value()));
        fields.put(RETAIN_FIELD_CONTENT, MqttCodecUtil.encode(MqttVersion.MQTT_3_1_1, publish));
        redisTemplate.opsForHash().putAll(retainKey, fields);
        redisTemplate.opsForSet().add(topicSetKey, topic);
    }

    /**
     * 订阅时回放匹配 filter 的 retain。
     */
    public List<MqttRetainStore> findRetainMatching(String appKey, String topicFilter) {
        if (StringUtils.isAnyBlank(appKey, topicFilter)) {
            return List.of();
        }
        Set<Object> topics = redisTemplate.opsForSet().members(CacheConstant.buildMqttRetainTopicSetCacheKey(appKey));
        if (topics == null || topics.isEmpty()) {
            return List.of();
        }
        List<MqttRetainStore> result = new ArrayList<>();
        for (Object topicObj : topics) {
            if (topicObj == null) {
                continue;
            }
            String topic = topicObj.toString();
            if (!MqttTopicFilterUtil.matches(topicFilter, topic)) {
                continue;
            }
            MqttRetainStore store = loadRetain(appKey, topic);
            if (store != null) {
                result.add(store);
            }
        }
        return result;
    }

    private MqttRetainStore loadRetain(String appKey, String topic) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(CacheConstant.buildMqttRetainCacheKey(appKey, topic));
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        Object content = entries.get(RETAIN_FIELD_CONTENT);
        if (content == null) {
            return null;
        }
        int qos = 0;
        Object qosObj = entries.get(RETAIN_FIELD_QOS);
        if (qosObj != null) {
            try {
                qos = Integer.parseInt(qosObj.toString());
            } catch (NumberFormatException ignored) {
                qos = 0;
            }
        }
        Object storedTopic = entries.get(RETAIN_FIELD_TOPIC);
        return new MqttRetainStore(storedTopic == null ? topic : storedTopic.toString(), qos, content.toString());
    }

    public int nextPacketId(String appKey, String comboIdentity) {
        Long seq = redisTemplate.opsForValue().increment(CacheConstant.buildMqttMessageIdCacheKey(appKey, comboIdentity));
        long raw = seq == null ? 1L : seq;
        int id = (int) ((raw % MessageConstant.MQTT_PACKET_ID_MAX) + 1);
        return id <= 0 ? 1 : id;
    }

    public void saveInflight(String appKey, String comboIdentity, int packetId, String encodedPublish) {
        if (StringUtils.isAnyBlank(appKey, comboIdentity, encodedPublish) || packetId <= 0) {
            return;
        }
        redisTemplate.opsForHash().put(CacheConstant.buildMqttInflightCacheKey(appKey, comboIdentity),
                String.valueOf(packetId), encodedPublish);
    }

    public void removeInflight(String appKey, String comboIdentity, int packetId) {
        if (StringUtils.isAnyBlank(appKey, comboIdentity) || packetId <= 0) {
            return;
        }
        redisTemplate.opsForHash().delete(CacheConstant.buildMqttInflightCacheKey(appKey, comboIdentity),
                String.valueOf(packetId));
    }

    public Map<Integer, String> loadInflight(String appKey, String comboIdentity) {
        if (StringUtils.isAnyBlank(appKey, comboIdentity)) {
            return Map.of();
        }
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(
                CacheConstant.buildMqttInflightCacheKey(appKey, comboIdentity));
        if (entries == null || entries.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> result = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            try {
                result.put(Integer.parseInt(entry.getKey().toString()), entry.getValue().toString());
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return result;
    }

    public void clearInflight(String appKey, String comboIdentity) {
        if (StringUtils.isAnyBlank(appKey, comboIdentity)) {
            return;
        }
        redisTemplate.delete(CacheConstant.buildMqttInflightCacheKey(appKey, comboIdentity));
    }

    /**
     * 按发布 topic 匹配所有订阅 filter，返回 comboIdentity（appKey:identity:deviceType）。
     */
    public List<MqttSubscriber> findPublishSubscribers(String appKey, String topicName) {
        Set<Object> filters = redisTemplate.opsForSet().members(CacheConstant.buildMqttTopicListCacheKey(appKey));
        if (filters == null || filters.isEmpty()) {
            return List.of();
        }
        List<MqttSubscriber> subscribers = new ArrayList<>();
        for (Object filterObj : filters) {
            if (filterObj == null) {
                continue;
            }
            String topicFilter = filterObj.toString();
            if (!MqttTopicFilterUtil.matches(topicFilter, topicName)) {
                continue;
            }
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(
                    CacheConstant.buildMqttTopicFilterCacheKey(appKey, topicFilter));
            if (entries == null || entries.isEmpty()) {
                continue;
            }
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                subscribers.add(new MqttSubscriber(entry.getKey().toString(), entry.getValue()));
            }
        }
        return subscribers;
    }

    public record MqttSubscriber(String comboIdentity, Object qos) {
    }

    public record MqttRetainStore(String topic, int qos, String content) {
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
