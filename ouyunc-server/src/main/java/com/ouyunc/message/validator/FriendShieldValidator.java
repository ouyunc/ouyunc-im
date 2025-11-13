package com.ouyunc.message.validator;

import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.domain.constants.YesOrNo;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * @author fzx
 * @description 屏蔽校验器
 */
public enum FriendShieldValidator implements ReactiveValidator<Packet> {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(FriendShieldValidator.class);

    /***
     * @author fzx
     * @description 校验是否在屏蔽, 被屏蔽返回 true, 未被屏蔽返回false
     * 优化：使用多级缓存查询，提高性能
     */
    @Override
    public Mono<Boolean> verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        Metadata metadata = message.getMetadata();
        String appKey = metadata.getAppKey();
        
        // 使用响应式多级缓存查询好友关系
        return DefaultRepository.INSTANCE.getFriendReactive(appKey, from, to)
                .flatMap(friendEntity -> {
                    if (friendEntity != null && YesOrNo.YES.getCode().equals(friendEntity.getShield())) {
                        log.warn("{} 已经被 {} 屏蔽了", from, to);
                        return Mono.just(true);
                    }
                    // 如果不是好友，也返回true（被屏蔽）
                    return Mono.just(friendEntity == null);
                })
                .defaultIfEmpty(true)
                .onErrorResume(e -> {
                    log.error("校验好友屏蔽状态异常, from: {}, to: {}", from, to, e);
                    return Mono.just(true); // 异常时默认拦截
                });
    }
}
