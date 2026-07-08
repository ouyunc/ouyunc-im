package com.ouyunc.message.validator;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.GroupRequestContent;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;

/**
 * 群邀请：邀请人（message.from）不能与被邀请人（content.identity）相同。
 */
public enum GroupInviteSelfValidator implements ReactiveValidator<Packet> {

    INSTANCE;

    @Override
    public Mono<Boolean> verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet != null ? packet.getMessage() : null;
        if (message == null) {
            return Mono.just(false);
        }
        String inviter = message.getFrom();
        String joiner = resolveJoinerIdentity(message);
        if (StringUtils.isAnyBlank(inviter, joiner)) {
            return Mono.just(false);
        }
        return Mono.just(StringUtils.equals(inviter, joiner));
    }

    private static String resolveJoinerIdentity(Message message) {
        if (message.getContentType() == MessageContentTypeEnum.GROUP_REQUEST_CONTENT.getType()) {
            try {
                Object parsed = JSON.parseObject(message.getContent(),
                        MessageContentTypeEnum.GROUP_REQUEST_CONTENT.getContentClass());
                if (parsed instanceof GroupRequestContent groupRequestContent) {
                    return groupRequestContent.getIdentity();
                }
            } catch (Exception ignored) {
                return null;
            }
        }
        try {
            GroupRequestContent content = JSON.parseObject(message.getContent(), GroupRequestContent.class);
            return content != null ? content.getIdentity() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
