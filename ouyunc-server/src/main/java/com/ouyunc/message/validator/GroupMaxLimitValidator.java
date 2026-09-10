package com.ouyunc.message.validator;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 用户可加入的群数量上限。超过返回 true（拒绝）。{@code maxPerUser < 0} 不限制。
 */
public enum GroupMaxLimitValidator implements ReactiveValidator<Packet> {
    INSTANCE;
    private static final Logger log = LoggerFactory.getLogger(GroupMaxLimitValidator.class);

    @Override
    public Mono<Boolean> verify(Packet packet, ChannelHandlerContext ctx) {
        if (packet == null || packet.getMessage() == null || packet.getMessage().getMetadata() == null) {
            return Mono.just(true);
        }
        int maxPerUser = MessageServerContext.serverProperties().getGroupMaxPerUser();
        if (maxPerUser < 0) {
            return Mono.just(false);
        }
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        String userId = message.getFrom();
        if (StringUtils.isAnyBlank(appKey, userId)) {
            return Mono.just(true);
        }
        return Mono.fromCallable(() -> {
                    long owned = DefaultRepository.INSTANCE.userGroupCount(appKey, userId);
                    if (owned >= maxPerUser) {
                        log.warn("用户加群数超限 appKey={} userId={} owned={} max={}", appKey, userId, owned, maxPerUser);
                        return true;
                    }
                    return false;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.error("校验用户加群上限异常 appKey={} userId={}", appKey, userId, e);
                    return Mono.just(true);
                });
    }
}
