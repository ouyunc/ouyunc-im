package com.ouyunc.message.validator;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.domain.constants.YesOrNo;
import com.ouyunc.domain.entity.FriendEntity;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * @author fzx
 * @description 屏蔽校验器
 */
public enum FriendShieldValidator implements ReactiveValidator<Packet> {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(FriendShieldValidator.class);

    private static final ReactiveRedisTemplate<String, FriendEntity> reactiveRedisTemplate = CacheFactory.REACTIVE_REDIS.instance();

    /***
     * @author fzx
     * @description 校验是否在屏蔽, 被屏蔽返回 true, 未被屏蔽返回false
     */
    @Override
    public Mono<Boolean> verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        Metadata metadata = message.getMetadata();
        String appKey = metadata.getAppKey();
        Mono<FriendEntity> friendEntityMono = reactiveRedisTemplate.opsForValue().get(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.FRIENDS_CONFIG + from + CacheConstant.COLON + to);
        return friendEntityMono
                .flatMap(friendEntity -> {
                    if (friendEntity != null && YesOrNo.YES.getCode().equals(friendEntity.getShield())) {
                        log.warn("{} 已经被 {} 屏蔽了", from, to);
                        return Mono.just(true);
                    }
                    return FriendValidator.INSTANCE.negate().verify(packet, ctx);
        }).defaultIfEmpty(true);
    }
}
