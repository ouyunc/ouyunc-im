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
 * 群成员数量上限。超过返回 true（拒绝）。{@code maxMembers < 0} 不限制。
 */
public enum GroupUserMaxLimitValidator implements ReactiveValidator<Packet> {
    INSTANCE;
    private static final Logger log = LoggerFactory.getLogger(GroupUserMaxLimitValidator.class);

    @Override
    public Mono<Boolean> verify(Packet packet, ChannelHandlerContext ctx) {
        if (packet == null || packet.getMessage() == null || packet.getMessage().getMetadata() == null) {
            return Mono.just(true);
        }
        int maxMembers = MessageServerContext.serverProperties().getGroupMaxMembers();
        if (maxMembers < 0) {
            return Mono.just(false);
        }
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        String groupId = message.getTo();
        if (StringUtils.isAnyBlank(appKey, groupId)) {
            return Mono.just(true);
        }
        return Mono.fromCallable(() -> {
                    long current = DefaultRepository.INSTANCE.groupMemberCount(appKey, groupId);
                    if (current >= maxMembers) {
                        log.warn("群成员数超限 appKey={} groupId={} current={} max={}", appKey, groupId, current, maxMembers);
                        return true;
                    }
                    return false;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.error("校验群成员上限异常 appKey={} groupId={}", appKey, groupId, e);
                    return Mono.just(true);
                });
    }
}
