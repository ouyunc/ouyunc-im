package com.ouyunc.message.validator;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.cache.config.CacheFactory;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * @author fzx
 * @description 黑名单校验器
 */
public enum BlackListValidator implements Validator<Packet> {

    INSTANCE;
    private static final Logger log = LoggerFactory.getLogger(BlackListValidator.class);

    /**
     * redisTemplate
     */
    private static final RedisTemplate<String, ?> redisTemplate = CacheFactory.REDIS.instance();

   /***
     * @author fzx
     * @description 校验是否在黑名单, 在黑名单 返回true， 不在黑名单，返回false
     */
    @Override
    public boolean verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        Metadata metadata = message.getMetadata();
        Long joinTimestamp = redisTemplate.<String, Long>opsForHash().get(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.BLACKLIST + message.getTo(), message.getFrom());
        if (joinTimestamp != null && joinTimestamp > 0) {
            log.warn("{} 在黑名单中，加入时间：{}", from, joinTimestamp);
            return true;
        }
        // 从数据库查询？可能会影响性能，为了最大提升性能，直接从redis中查询，没有就认为不在黑名单，这个功能不是最重要的
        return false;
    }
}
