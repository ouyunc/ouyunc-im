package com.ouyunc.message.validator;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.context.RelationLocalCache;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * 黑名单校验器：本地布尔缓存 miss 再 Redis HGET。
 */
public enum BlackListValidator implements ReactiveValidator<Packet> {

    INSTANCE;
    private static final Logger log = LoggerFactory.getLogger(BlackListValidator.class);

    private static final ReactiveRedisTemplate<String, ?> reactiveRedisTemplate = CacheFactory.REACTIVE_REDIS.instance();

    /**
     * 校验是否在黑名单：在黑名单返回 true，不在返回 false。
     */
    @Override
    public Mono<Boolean> verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        Metadata metadata = message.getMetadata();
        String appKey = metadata.getAppKey();
        Boolean cached = RelationLocalCache.BLACKLIST.get(RelationLocalCache.blacklistKey(appKey, to, from));
        if (cached != null) {
            if (cached) {
                log.warn("{} 在 {} 的黑名单缓存中", from, to);
            }
            return Mono.just(cached);
        }
        Mono<Long> joinTimestampMono = reactiveRedisTemplate.<String, Long>opsForHash()
                .get(CacheConstant.buildBlacklistCacheKey(appKey, to), from);
        return joinTimestampMono
                .map(joinTimestamp -> {
                    boolean listed = joinTimestamp != null && joinTimestamp > 0;
                    if (listed) {
                        log.warn("{} 在黑名单中，加入时间：{}", from, joinTimestamp);
                    }
                    return listed;
                })
                .defaultIfEmpty(false)
                .doOnNext(listed -> RelationLocalCache.markBlacklist(appKey, to, from, listed));
    }
}
