package com.ouyunc.message.validator;

import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.domain.constants.GroupStatus;
import com.ouyunc.domain.constants.YesOrNo;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * @author fzx
 * @description 禁言校验器
 */
public enum GroupSilenceValidator implements ReactiveValidator<Packet> {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(GroupSilenceValidator.class);

    /***
     * @author fzx
     * @description 校验是否被禁言，或者群组被封禁（由于触犯违法行为）
     * 优化：使用多级缓存查询，提高性能
     */
    @Override
    public Mono<Boolean> verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        Metadata metadata = message.getMetadata();
        String appKey = metadata.getAppKey();
        
        // 使用响应式多级缓存查询群组信息
        return DefaultRepository.INSTANCE.getGroupEntityReactive(appKey, to)
                .flatMap(groupEntity -> {
                    if (groupEntity == null) {
                        log.warn("群组 {} 不存在", to);
                        return Mono.just(true);
                    }
                    if (GroupStatus.ABNORMAL.value().equals(groupEntity.getStatus())) {
                        log.warn("{} 已经被平台封禁", to);
                        return Mono.just(true);
                    } else if (YesOrNo.YES.getCode().equals(groupEntity.getSilence())) {
                        log.warn("该群 {} 已经全部禁言", to);
                        return Mono.just(true);
                    }
                    // 使用响应式多级缓存查询群成员信息，检查是否被单独禁言
                    return DefaultRepository.INSTANCE.groupUserEntityReactive(appKey, to, from)
                            .flatMap(groupUserEntity -> {
                                if (groupUserEntity != null && YesOrNo.YES.getCode().equals(groupUserEntity.getSilence())) {
                                    log.warn("{} 已经被 {} 禁言", from, to);
                                    return Mono.just(true);
                                }
                                return Mono.just(false);
                            })
                            .defaultIfEmpty(true);
                })
                .defaultIfEmpty(true)
                .onErrorResume(e -> {
                    log.error("校验群组禁言状态异常, appKey: {}, groupId: {}, userId: {}", appKey, to, from, e);
                    return Mono.just(true); // 异常时默认拦截
                });
    }
}
