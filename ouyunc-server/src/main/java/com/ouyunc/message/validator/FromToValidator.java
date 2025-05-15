package com.ouyunc.message.validator;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import io.netty.channel.ChannelHandlerContext;
import reactor.core.publisher.Mono;

/**
 * @author fzx
 * @description 发送者和接受者校验器，发送方和接收方不能是同一个人
 */
public enum FromToValidator implements ReactiveValidator<Packet> {

    INSTANCE;


    /***
     * @author fzx
     * @description 校验发送方是否等于接收方
     */
    @Override
    public Mono<Boolean> verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        return Mono.just(from.equals(to));
    }
}
