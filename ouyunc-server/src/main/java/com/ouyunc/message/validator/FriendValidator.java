package com.ouyunc.message.validator;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.cache.config.CacheFactory;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * @author fzx
 * @description 好友校验
 */
public enum FriendValidator implements ReactiveValidator<Packet> {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(FriendValidator.class);

    private static final ReactiveStringRedisTemplate reactiveStringRedisTemplate = CacheFactory.REACTIVE_STRING_REDIS.instance();


    /***
     * @author fzx
     * @description 校验是否是好友，是好友返回true, 否则返回false
     */
    @Override
    public Mono<Boolean> verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        Metadata metadata = message.getMetadata();
        String appKey = metadata.getAppKey();
        String friendsKey = CacheConstant.buildFriendsCacheKey(appKey, to);
        return reactiveStringRedisTemplate.opsForZSet().score(friendsKey, from)
                .flatMap(score -> {
                    if (score != null && score > NumberConstant.NUMBER_0) {
                        return Mono.just(true);
                    }
                    log.warn("校验好友关系失败，{} 和 {} 不是好友关系, appKey={}, cacheKey={}, score={}",
                            from, to, appKey, friendsKey, score);
                    return Mono.just(false);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("校验好友关系失败，{} 和 {} 不是好友关系, appKey={}, cacheKey={}, redisScore=empty",
                            from, to, appKey, friendsKey);
                    return Mono.just(false);
                }));
    }
}
