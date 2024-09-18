package com.ouyunc.message.processor;

import com.ouyunc.base.model.ProtocolType;
import com.ouyunc.base.packet.Packet;

import java.util.List;

/**
 * 委派消息内容处理器链
 */
final public class DelegatingMessageContentProcessorChain implements ProcessorChain<AbstractBaseProcessor<? extends Number>> {


    private final ProtocolType protocolType;


    private final List<AbstractBaseProcessor<? extends Number>> delegates;

    public DelegatingMessageContentProcessorChain(ProtocolType protocolType, List<AbstractBaseProcessor<? extends Number>> processors) {
        this.protocolType = protocolType;
        this.delegates = processors;
    }


    /**
     * 匹配器
     */
    @Override
    public boolean matches(Packet packet) {
        return this.protocolType.getProtocol() == packet.getProtocol() && this.protocolType.getProtocolVersion() == packet.getProtocolVersion();
    }

    /**
     * 获取相同协议的所有消息内容处理器
     */
    @Override
    public List<AbstractBaseProcessor<? extends Number>> getProcessors() {
        return this.delegates;
    }

}
