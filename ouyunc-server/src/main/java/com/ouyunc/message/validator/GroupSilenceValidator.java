package com.ouyunc.message.validator;

import com.ouyunc.base.constant.enums.GroupStatus;
import com.ouyunc.base.constant.enums.GroupUserPost;
import com.ouyunc.base.constant.enums.YesOrNo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.domain.entity.GroupUserEntity;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 群禁言校验：全员禁言放行群主/管理员；成员级禁言仍拦截。
 */
public enum GroupSilenceValidator implements ReactiveValidator<Packet> {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(GroupSilenceValidator.class);

    @Override
    public Mono<Boolean> verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        Metadata metadata = message.getMetadata();
        String appKey = metadata.getAppKey();

        return DefaultRepository.INSTANCE.getGroupEntityReactive(appKey, to)
                .flatMap(groupEntity -> {
                    if (groupEntity == null) {
                        log.warn("群组 {} 不存在", to);
                        return Mono.just(true);
                    }
                    if (GroupStatus.ABNORMAL.value().equals(groupEntity.getStatus())) {
                        log.warn("{} 已经被平台封禁", to);
                        return Mono.just(true);
                    }
                    return DefaultRepository.INSTANCE.groupUserEntityReactive(appKey, to, from)
                            .flatMap(groupUserEntity -> Mono.just(rejectBySilence(groupEntity.getSilence(),
                                    groupUserEntity, from, to)))
                            .switchIfEmpty(Mono.defer(() -> {
                                log.warn("群成员 {} 不在群 {} 内或查询无结果, appKey={}", from, to, appKey);
                                return Mono.just(true);
                            }));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("群组 {} 不存在或查询无结果, appKey={}", to, appKey);
                    return Mono.just(true);
                }))
                .onErrorResume(e -> {
                    log.error("校验群组禁言状态异常, appKey: {}, groupId: {}, userId: {}", appKey, to, from, e);
                    return Mono.just(true);
                });
    }

    /**
     * @return true 拦截发送
     */
    private static boolean rejectBySilence(Integer groupSilence, GroupUserEntity member,
                                           String from, String groupId) {
        if (member != null && YesOrNo.YES.getCode().equals(member.getSilence())) {
            log.warn("{} 已经被 {} 禁言", from, groupId);
            return true;
        }
        if (YesOrNo.YES.getCode().equals(groupSilence)
                && !GroupUserPost.isManagerOrLeader(member == null ? null : member.getPost())) {
            log.warn("该群 {} 已经全部禁言", groupId);
            return true;
        }
        return false;
    }
}
