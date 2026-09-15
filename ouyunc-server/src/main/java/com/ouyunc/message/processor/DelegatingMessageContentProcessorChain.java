package com.ouyunc.message.processor;

import com.ouyunc.base.model.MessageProtocol;
import com.ouyunc.base.packet.Packet;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 委派消息内容处理器链（process 返回 {@link Mono}{@code <Void>}）。
 */
public final class DelegatingMessageContentProcessorChain
        implements ProcessorChain<AbstractBaseBiProcessor<Mono<Void>, ? extends Number>> {

    private final MessageProtocol messageProtocol;
    private final List<AbstractBaseBiProcessor<Mono<Void>, ? extends Number>> delegates;

    public DelegatingMessageContentProcessorChain(MessageProtocol messageProtocol,
                                                  List<AbstractBaseBiProcessor<Mono<Void>, ? extends Number>> processors) {
        this.messageProtocol = messageProtocol;
        this.delegates = processors;
    }

    @Override
    public boolean matches(Packet packet) {
        return this.messageProtocol.getProtocol() == packet.getProtocol()
                && this.messageProtocol.getProtocolVersion() == packet.getProtocolVersion();
    }

    @Override
    public List<AbstractBaseBiProcessor<Mono<Void>, ? extends Number>> getProcessors() {
        return this.delegates;
    }
}
