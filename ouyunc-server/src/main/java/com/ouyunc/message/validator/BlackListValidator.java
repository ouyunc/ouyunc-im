package com.ouyunc.message.validator;

import com.ouyunc.base.constant.enums.IdentityType;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 黑名单校验器：完整 INIT 下 miss 才可放行；不确定则 fail-closed。
 */
public enum BlackListValidator implements ReactiveValidator<Packet> {

    INSTANCE;
    private static final Logger log = LoggerFactory.getLogger(BlackListValidator.class);

    /**
     * 校验是否在黑名单：在黑名单返回 true，不在返回 false。查询异常视为在黑名单。
     */
    @Override
    public Mono<Boolean> verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        Metadata metadata = message.getMetadata();
        String appKey = metadata.getIngress().getAppKey();
        // 业务黑名单只按用户对用户 identity_type=1 写入；群/客服 to 不是 owner，禁止回源错误索引
        if (!isUserOwnerBlacklist(packet)) {
            return Mono.just(Boolean.FALSE);
        }
        int identityType = IdentityType.ONE_2_ONE.value();
        return DefaultRepository.INSTANCE.isBlacklistedReactive(appKey, to, from, identityType)
                .doOnNext(listed -> {
                    if (Boolean.TRUE.equals(listed)) {
                        log.warn("{} 在 {} 的黑名单中", from, to);
                    }
                })
                .onErrorResume(e -> {
                    log.error("黑名单查询异常 from={} to={}", from, to, e);
                    return Mono.just(Boolean.TRUE);
                });
    }

    /**
     * 黑名单作用域由服务端业务类型决定，禁止客户端通过伪造 toType 跳过好友请求校验。
     */
    private static boolean isUserOwnerBlacklist(Packet packet) {
        return packet.getMessageType() == com.ouyunc.base.constant.enums.MessageTypeEnum.ONE_2_ONE.getType()
                || com.ouyunc.base.constant.MqArchiveRouting.isFriendRequestType(packet.getMessageType());
    }
}
