package com.ouyunc.repository;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.LuaScriptConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.cache.config.CacheFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.ArrayList;
import java.util.List;
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
        // 保存原始消息到influxdb 中
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

    /**
     * 撤销消息
     * @param packetIds 需要撤销的消息id
     */
    public boolean withdrawMessage(List<String> packetIds) {
        return false;
    }

    /**
     * 保存业务消息,保存到redis中，并设置过期时间
     * @param packet
     * @return
     */
    @SuppressWarnings("unchecked")
    public boolean saveMessage(Packet packet, boolean qosEnable) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();

        String luaScript = LuaScriptConstant.SAVE_MESSAGE_LUA_SCRIPT;

        List<String> keys = new ArrayList<>();
        keys.add(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + packet.getPacketId());
        keys.add(CacheConstant.OUYUNC + CacheConstant.SESSION + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + IdentityUtil.sessionId(message.getFrom(), message.getTo()));

        RedisSerializer<Object> valueSerializer = (RedisSerializer<Object>) redisTemplate.getValueSerializer();
        String businessMessage = new String(Objects.requireNonNull(valueSerializer.serialize(packet)));
        long expireTime = 1000L * 60;
        String paketIdStr = new String(Objects.requireNonNull(valueSerializer.serialize(packet.getPacketId())));
        String serverTimeStr = new String(Objects.requireNonNull(valueSerializer.serialize(metadata.getServerTime())));

        List<Object> args = new ArrayList<>();
        args.add(businessMessage);
        args.add(expireTime);
        args.add(paketIdStr);
        args.add(metadata.getServerTime());
        if (qosEnable && message.getQos() > NumberConstant.NUMBER_0) {
            // 保存离线消息
            // 准备键和参数
            keys.add(CacheConstant.OUYUNC + CacheConstant.OFFLINE + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + message.getTo());
            args.add(paketIdStr);
            args.add(serverTimeStr);
        }
        // 保存消息
        Boolean execute = redisTemplate.execute(new DefaultRedisScript<>(luaScript, Boolean.class), keys, args.toArray());
        System.out.println(execute);
        return true;
    }

    /**
     * 保存离线消息
     * @param packet
     * @return
     */
    public boolean saveOfflineMessage(Packet packet) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return Boolean.TRUE.equals(redisTemplate.opsForZSet().add(CacheConstant.OUYUNC + CacheConstant.OFFLINE + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + message.getTo(), packet.getPacketId(), metadata.getServerTime()));
    }

    /**
     * 保存会话消息
     * @param packet
     * @return
     */
    public boolean saveSessionMessage(String sessionId, Packet packet) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return Boolean.TRUE.equals(redisTemplate.opsForZSet().add(CacheConstant.OUYUNC + CacheConstant.OFFLINE + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + sessionId, packet.getPacketId(), metadata.getServerTime()));
    }
}
