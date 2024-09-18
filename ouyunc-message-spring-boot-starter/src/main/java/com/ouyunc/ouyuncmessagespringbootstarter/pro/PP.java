package com.ouyunc.ouyuncmessagespringbootstarter.pro;

import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.Type;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.processor.AbstractMessageProcessor;
import io.netty.channel.ChannelHandlerContext;

public class PP extends AbstractMessageProcessor<Byte> {
    @Override
    public MessageType type() {
        return TypeEnum.PING_PONG;
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {

    }
}
