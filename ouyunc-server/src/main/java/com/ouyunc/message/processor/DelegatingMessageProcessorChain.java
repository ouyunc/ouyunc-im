package com.ouyunc.message.processor;

import com.ouyunc.base.model.MessageProtocol;
import com.ouyunc.base.packet.Packet;

import java.util.List;

/**
 * 委派消息处理器链
 */
public final class DelegatingMessageProcessorChain implements ProcessorChain<AbstractMessageProcessor<? extends Number>> {

    private final MessageProtocol messageProtocol;

    private final List<AbstractMessageProcessor<? extends Number>> delegates;

    public DelegatingMessageProcessorChain(MessageProtocol messageProtocol, List<AbstractMessageProcessor<? extends Number>> processors) {
        this.messageProtocol = messageProtocol;
        this.delegates = processors;
    }


    /**
     * 根据相同协议的消息类型是一个过滤器链
     */
    @Override
    public boolean matches(Packet packet) {
        return this.messageProtocol.getProtocol() == packet.getProtocol() && this.messageProtocol.getProtocolVersion() == packet.getProtocolVersion();
    }

    @Override
    public List<AbstractMessageProcessor<? extends Number>> getProcessors() {
        return this.delegates;
    }


}
