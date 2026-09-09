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
 * 好友校验：Caffeine / friendEntityCache 命中后才打 Redis ZSCORE。
 */
public enum FriendValidator implements ReactiveValidator<Packet> {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(FriendValidator.class);

    /**
     * 校验是否是好友：发送方须在接收方好友 ZSET 中。是好友返回 true，否则 false。
     */
    @Override
    public Mono<Boolean> verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        Metadata metadata = message.getMetadata();
        String appKey = metadata.getAppKey();
        return Mono.fromCallable(() -> DefaultRepository.INSTANCE.isFriend(appKey, to, from))
                .doOnNext(friend -> {
                    if (!Boolean.TRUE.equals(friend)) {
                        log.warn("校验好友关系失败，{} 和 {} 不是好友关系, appKey={}", from, to, appKey);
                    }
                });
    }
}
