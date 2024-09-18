package com.ouyunc.message.processor;

import com.ouyunc.base.model.ProtocolType;
import com.ouyunc.base.packet.Packet;

import java.util.List;

/**
 * 委派消息处理器链
 */
final public class DelegatingMessageProcessorChain implements ProcessorChain<AbstractMessageProcessor<? extends Number>> {

    private final ProtocolType protocolType;

    private final List<AbstractMessageProcessor<? extends Number>> delegates;

    public DelegatingMessageProcessorChain(ProtocolType protocolType, List<AbstractMessageProcessor<? extends Number>> processors) {
        this.protocolType = protocolType;
        this.delegates = processors;
    }


    /**
     * 根据相同协议的消息类型是一个过滤器链
     */
    @Override
    public boolean matches(Packet packet) {
        return this.protocolType.getProtocol() == packet.getProtocol() && this.protocolType.getProtocolVersion() == packet.getProtocolVersion();
    }

    @Override
    public List<AbstractMessageProcessor<? extends Number>> getProcessors() {
        return this.delegates;
    }


}
