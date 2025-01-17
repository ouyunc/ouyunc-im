package com.ouyunc.ouyuncmessagespringbootstarter.pro;


import com.ouyunc.message.processor.AbstractMessageProcessor;

public class PP extends AbstractMessageProcessor<Byte> {
    @Override
    public MessageType type() {
        return TypeEnum.PING_PONG;
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {

    }
}
