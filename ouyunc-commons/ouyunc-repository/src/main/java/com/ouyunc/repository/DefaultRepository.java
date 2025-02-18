package com.ouyunc.repository;

import com.google.common.collect.Lists;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.LuaScriptConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.QosLevelEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.context.MessageContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author fzx
 * @description 默认持久化仓库实现,注意如果子类不进行覆盖，则使用默认的操作器来处理数据
 */
public enum DefaultRepository implements Repository{
    INSTANCE;

    private static final RedisTemplate<String, ?> redisTemplate = CacheFactory.REDIS.instance();

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
        Double score = redisTemplate.opsForZSet().score(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON  + CacheConstant.OFFLINE + message.getTo(), packet.getPacketId());
        // 如果分数不为 null，则表示值存在
        return !Objects.isNull(score);
    }

    /**
     * 撤销消息
     */
    public boolean withdrawMessage(Packet packet, String sessionId) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();


        return redisTemplate.execute(new DefaultRedisScript<>(LuaScriptConstant.WITHDRAW_MESSAGE_LUA_SCRIPT, Boolean.class), keys, args);
    }


    /**
     * 获取群组用户列表的用户唯一标识，这里直接从缓存中取，获取不到就失败，不需要再从数据库中获取，如果有需要可以做多级缓存
     * @param packet
     * @return
     */
    @SuppressWarnings("unchecked")
    public Set<String> groupUsersIdentity(Packet packet) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        // score 存储的是用户加入群的时间戳，毫秒
        return  (Set<String>) redisTemplate.opsForZSet().range(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.GROUP_USERS + message.getTo(), NumberConstant.NUMBER_0, NumberConstant.NUMBER_NEGATIVE_1);
    }


    /**
     * 保存业务消息以及离线消息和会话消息
     * @param packet
     * @param expireTime 过期时间，单位毫秒，多久后过期
     * @return
     */
    public boolean saveMessage(Packet packet, String sessionId, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String luaScript = LuaScriptConstant.SAVE_MESSAGE_WITHOUT_OFFLINE_LUA_SCRIPT;
        List<String> keys = Lists.newArrayList(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.MESSAGE + packet.getPacketId(),
                CacheConstant.OUYUNC +  CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.SESSION + sessionId);
        Object[] args = new Object[]{packet, expireTime, packet.getPacketId(), metadata.getServerTime()};
        // 如果开启qos,并且需要qos
        if (MessageContext.messageProperties.isQosEnable() && message.getQos() > QosLevelEnum.QOS_0.getLevel()) {
            keys.add(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.OFFLINE + message.getTo());
            luaScript = LuaScriptConstant.SAVE_MESSAGE_WITH_OFFLINE_LUA_SCRIPT;
        }
        return redisTemplate.execute(new DefaultRedisScript<>(luaScript, Boolean.class), keys, args);
    }




}
