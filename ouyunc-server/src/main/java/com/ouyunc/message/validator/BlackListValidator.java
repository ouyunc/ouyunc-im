package com.ouyunc.message.validator;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.cache.config.CacheFactory;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * @author fzx
 * @description 黑名单校验器
 */
public enum BlackListValidator implements ReactiveValidator<Packet> {

    INSTANCE;
    private static final Logger log = LoggerFactory.getLogger(BlackListValidator.class);

    /**
     * redisTemplate
     */
    private static final ReactiveRedisTemplate<String, ?> reactiveRedisTemplate = CacheFactory.REACTIVE_REDIS.instance();

   /***
     * @author fzx
     * @description 校验是否在黑名单, 在黑名单 返回true， 不在黑名单，返回false
     */
    @Override
    public Mono<Boolean> verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        Metadata metadata = message.getMetadata();
        Mono<Long> joinTimestampMono = reactiveRedisTemplate.<String, Long>opsForHash().get(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.BLACKLIST + message.getTo(), message.getFrom());
        return joinTimestampMono
                .map(joinTimestamp -> {
                    if (joinTimestamp != null && joinTimestamp > 0) {
                        log.warn("{} 在黑名单中，加入时间：{}", from, joinTimestamp);
                        return true;
                    }
                    return false;
                })
                .defaultIfEmpty(false);
    }
}
