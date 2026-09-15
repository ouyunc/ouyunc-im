package com.ouyunc.ouyuncmessagespringbootstarter.pro;

import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.processor.AbstractMessageBiProcessor;
import io.netty.channel.ChannelHandlerContext;
import reactor.core.publisher.Mono;

/**
 * 示例 Processor（starter 演示用）。
 */
public class PP extends AbstractMessageBiProcessor<Byte> {
    @Override
    public MessageType type() {
        return MessageTypeEnum.PING_PONG;
    }

    @Override
    public Mono<Void> process(ChannelHandlerContext ctx, Packet packet) {
        return Mono.empty();
    }
}
