package com.ouyunc.message.validator;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.domain.constants.GroupStatus;
import com.ouyunc.domain.constants.YesOrNo;
import com.ouyunc.domain.entity.GroupEntity;
import com.ouyunc.domain.entity.GroupUserEntity;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * @author fzx
 * @description 禁言校验器
 */
public enum GroupSilenceValidator implements ReactiveValidator<Packet> {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(GroupSilenceValidator.class);

    private static final ReactiveRedisTemplate<String, ?> reactiveRedisTemplate = CacheFactory.REACTIVE_REDIS.instance();

    /***
     * @author fzx
     * @description 校验是否被禁言，或者群组被封禁（由于触犯违法行为）
     */
    @SuppressWarnings("unchecked")
    @Override
    public Mono<Boolean> verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        Metadata metadata = message.getMetadata();
        String appKey = metadata.getAppKey();
        // 获取群组的信息
        Mono<GroupEntity> groupEntityMono = (Mono<GroupEntity>) reactiveRedisTemplate.opsForValue().get(CacheConstant.buildGroupCacheKey(appKey, to));
        return groupEntityMono
                .switchIfEmpty(DefaultRepository.INSTANCE.getGroupEntityFromDatabasesReactive(appKey, to))
                .flatMap(groupEntity -> {
                    if (groupEntity == null) {
                        log.warn("群组 {} 不存在", to);
                        return Mono.just(true);
                    }
                    if (GroupStatus.ABNORMAL.value().equals(groupEntity.getStatus())) {
                        log.warn("{} 已经被平台封禁", to);
                        return Mono.just(true);
                    }else if (YesOrNo.YES.getCode().equals(groupEntity.getSilence())) {
                        log.warn("该群 {} 已经全部禁言", to);
                        return Mono.just(true);
                    }
                    // 在缓存中获取群组用户信息，是否被单独禁言
                    Mono<GroupUserEntity> groupUserEntityMono = (Mono<GroupUserEntity>) reactiveRedisTemplate.opsForValue().get(CacheConstant.buildGroupUserConfigCacheKey(appKey, from, to));
                    return groupUserEntityMono.flatMap(groupUserEntity -> {
                        if (groupUserEntity != null && YesOrNo.YES.getCode().equals(groupUserEntity.getSilence())) {
                            log.warn("{} 已经被 {} 禁言", from, to);
                            return Mono.just(true);
                        }
                        return Mono.just(false);
                    }).defaultIfEmpty(true);
        }).defaultIfEmpty(true);
    }
}
