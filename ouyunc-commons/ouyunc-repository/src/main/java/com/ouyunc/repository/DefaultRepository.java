package com.ouyunc.repository;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.cache.config.CacheFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Objects;

/**
 * @author fzx
 * @description 默认持久化仓库实现,注意如果子类不进行覆盖，则使用默认的操作器来处理数据
 */
public enum DefaultRepository implements Repository{
    INSTANCE;

    private static final RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();

    @Override
    public void save(Packet packet) {

    }

    /**
     * 检查消息是否重复,是否持久化到缓存，这里是离线队列中
     * @param packet
     * @return
     */
    @Override
    public boolean checkDup(Packet packet) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        Double score = redisTemplate.opsForZSet().score(CacheConstant.OUYUNC + CacheConstant.OFFLINE + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + message.getTo(), packet.getPacketId());
        // 如果分数不为 null，则表示值存在
        return !Objects.isNull(score);
    }
}
