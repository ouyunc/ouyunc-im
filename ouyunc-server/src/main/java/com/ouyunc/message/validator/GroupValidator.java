package com.ouyunc.message.validator;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.domain.constants.GroupStatus;
import com.ouyunc.domain.entity.GroupEntity;
import com.ouyunc.repository.DefaultRepository;
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
    @SuppressWarnings("unchecked")
    @Override
    public Mono<Boolean> verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        String to = message.getTo();
        Metadata metadata = message.getMetadata();
        Mono<GroupEntity> groupEntityMono = (Mono<GroupEntity>) reactiveRedisTemplate.opsForValue().get(CacheConstant.buildGroupCacheKey(metadata.getAppKey(), to));
        return groupEntityMono
                .switchIfEmpty(DefaultRepository.INSTANCE.getGroupEntityFromDatabasesReactive(metadata.getAppKey(), to))
                .flatMap(groupEntity -> {
                    if (groupEntity == null) {
                        log.warn("群组 {}（appKey:{}）不存在或已被删除", to, metadata.getAppKey());
                        return Mono.just(true);
                    }
                    // 处理 status 为 null 的场景，默认判定为封禁
                    if (groupEntity.getStatus() == null || !GroupStatus.NORMAL.value().equals(groupEntity.getStatus())) {
                        log.warn("群组 {}（appKey:{}）已被平台封禁，当前状态：{}", to, metadata.getAppKey(), groupEntity.getStatus());
                        return Mono.just(true);
                    }
                    return Mono.just(false);
                })
                // 仅捕获流错误（如数据库查询失败），返回默认拦截结果
                .onErrorResume(e -> {
                    log.error("校验群组 {}（appKey:{}）封禁状态时发生异常", to, metadata.getAppKey(), e);
                    return Mono.just(true);
                })
                .defaultIfEmpty(true);
    }
}
