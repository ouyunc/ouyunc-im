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
 * 私聊热路径：一次本地缓存或 Redis Pipeline 得到好友/拉黑/屏蔽，命中拒绝则返回 true。
 */
public enum One2OneChatAccessValidator implements ReactiveValidator<Packet> {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(One2OneChatAccessValidator.class);

    @Override
    public Mono<Boolean> verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String appKey = metadata.getAppKey();
        return DefaultRepository.INSTANCE.loadOne2OneChatAccess(appKey, message.getFrom(), message.getTo())
                .map(access -> {
                    if (access.rejectSend()) {
                        log.warn("私聊准入拒绝 from={} to={} reason={}",
                                message.getFrom(), message.getTo(), access.rejectReason());
                        return Boolean.TRUE;
                    }
                    return Boolean.FALSE;
                })
                .defaultIfEmpty(Boolean.TRUE)
                .onErrorResume(e -> {
                    log.error("私聊准入查询异常 from={} to={}", message.getFrom(), message.getTo(), e);
                    return Mono.just(Boolean.TRUE);
                });
    }
}
