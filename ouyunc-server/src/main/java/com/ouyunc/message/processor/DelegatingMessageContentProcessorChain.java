package com.ouyunc.message.processor;

import com.ouyunc.base.model.MessageProtocol;
import com.ouyunc.base.packet.Packet;

import java.util.List;

/**
 * 委派消息内容处理器链
 */
public final class DelegatingMessageContentProcessorChain implements ProcessorChain<AbstractBaseProcessor<? extends Number>> {


    private final MessageProtocol messageProtocol;


    private final List<AbstractBaseProcessor<? extends Number>> delegates;

    public DelegatingMessageContentProcessorChain(MessageProtocol messageProtocol, List<AbstractBaseProcessor<? extends Number>> processors) {
        this.messageProtocol = messageProtocol;
        this.delegates = processors;
    }


    /**
     * 匹配器
     */
    @Override
    public boolean matches(Packet packet) {
        return this.messageProtocol.getProtocol() == packet.getProtocol() && this.messageProtocol.getProtocolVersion() == packet.getProtocolVersion();
    }

    /**
     * 获取相同协议的所有消息内容处理器
     */
    @Override
    public List<AbstractBaseProcessor<? extends Number>> getProcessors() {
        return this.delegates;
    }

}
