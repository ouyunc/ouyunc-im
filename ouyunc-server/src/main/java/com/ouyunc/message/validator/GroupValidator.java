package com.ouyunc.message.validator;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.domain.constants.GroupStatus;
import com.ouyunc.domain.entity.GroupEntity;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * @author fzx
 * @description 群状态校验
 */
public enum GroupValidator implements ReactiveValidator<Packet> {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(GroupValidator.class);

    private static final ReactiveRedisTemplate<String, ?> reactiveRedisTemplate = CacheFactory.REACTIVE_REDIS.instance();


    /***
     * @author fzx
     * @description 校验是否是在群是否被封禁，平台封禁返回true, 否则返回false
     */
    @Override
    public Mono<Boolean> verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        String to = message.getTo();
        Metadata metadata = message.getMetadata();
        Mono<GroupEntity> groupEntityMono = (Mono<GroupEntity>) reactiveRedisTemplate.opsForValue().get(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.GROUP + to);
        return groupEntityMono.flatMap(groupEntity -> {
                    if (groupEntity != null && GroupStatus.NORMAL.value().equals(groupEntity.getStatus())) {
                        return Mono.just(false);
                    }
                    log.warn("{} 已经被平台封禁", to);
                    return Mono.just(true);
                }).doOnNext(groupEntity -> {
                    if (groupEntity == null) {
                        log.warn("群组 {} 不存在或已被删除", to);
                    }
                }).doOnSuccess(groupEntity -> {
                    if (groupEntity == null) {
                        log.warn("群组 {} 缓存未找到，可能已被删除或不存在", to);
                    }
                }).defaultIfEmpty(true);
    }
}
