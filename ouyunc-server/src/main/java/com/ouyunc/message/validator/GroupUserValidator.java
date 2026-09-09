package com.ouyunc.message.validator;

import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 群成员校验：Caffeine / 群成员 identity 缓存 miss 再 Redis ZSCORE。
 */
public enum GroupUserValidator implements ReactiveValidator<Packet> {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(GroupUserValidator.class);

    /**
     * 校验是否在群内，在群中返回 true，否则 false。
     */
    @Override
    public Mono<Boolean> verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        Metadata metadata = message.getMetadata();
        String appKey = metadata.getAppKey();
        return DefaultRepository.INSTANCE.isGroupMemberReactive(appKey, to, from)
                .doOnNext(member -> {
                    if (!Boolean.TRUE.equals(member)) {
                        log.warn("校验群成员失败，{} 不在群 {} 内, appKey={}", from, to, appKey);
                    }
                });
    }
}
