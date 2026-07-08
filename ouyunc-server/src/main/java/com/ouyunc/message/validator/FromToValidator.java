package com.ouyunc.message.validator;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;

/**
 * @author fzx
 * @description 发送者和接受者校验器，发送方和接收方不能是同一个人
 */
public enum FromToValidator implements ReactiveValidator<Packet> {

    INSTANCE;

    @Override
    public Mono<Boolean> verify(Packet packet, ChannelHandlerContext ctx) {
        if (packet == null || packet.getMessage() == null) {
            return Mono.just(false);
        }
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        if (StringUtils.isAnyBlank(from, to)) {
            return Mono.just(false);
        }
        return Mono.just(StringUtils.equals(from, to));
    }
}
