package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.ProtocolType;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.processor.BiProcessor;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 处理器链代理：对多协议/多实现统一走 {@code Mono<Void>} 三阶段。
 *
 * @param <T> 链内处理器，process 返回 {@link Mono}{@code <Void>}
 */
public final class BiProcessorChainProxy<T extends BiProcessor<ChannelHandlerContext, Packet, Mono<Void>>>
        extends AbstractMessageBiProcessor<Number> {
    private static final Logger log = LoggerFactory.getLogger(BiProcessorChainProxy.class);

    private final ProtocolType<? extends Number> type;
    private final List<ProcessorChain<T>> processorChains;

    public BiProcessorChainProxy(List<ProcessorChain<T>> processorChains, ProtocolType<? extends Number> type) {
        this.type = type;
        this.processorChains = processorChains;
    }

    @Override
    public ProtocolType<? extends Number> type() {
        return type;
    }

    /**
     * 链式 preProcess：任一返回 false 则短路。
     */
    @SuppressWarnings("unchecked")
    @Override
    public Mono<Boolean> preProcess(ChannelHandlerContext ctx, Packet packet) {
        List<AbstractMessageBiProcessor<Byte>> processors = (List<AbstractMessageBiProcessor<Byte>>) getProcessors(packet);
        if (CollectionUtils.isEmpty(processors)) {
            return Mono.just(false);
        }
        Mono<Boolean> chain = Mono.just(true);
        for (AbstractMessageBiProcessor<Byte> processor : processors) {
            chain = chain.flatMap(passed -> {
                if (!Boolean.TRUE.equals(passed)) {
                    return Mono.just(false);
                }
                return processor.preProcess(ctx, packet);
            });
        }
        return chain;
    }

    @Override
    public Mono<Void> process(ChannelHandlerContext ctx, Packet packet) {
        List<T> processors = getProcessors(packet);
        if (CollectionUtils.isEmpty(processors)) {
            return Mono.empty();
        }
        Mono<Void> chain = Mono.empty();
        for (T processor : processors) {
            chain = chain.then(Mono.defer(() -> processor.process(ctx, packet)));
        }
        return chain;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Mono<Void> postProcess(ChannelHandlerContext ctx, Packet packet) {
        List<AbstractMessageBiProcessor<Byte>> processors = (List<AbstractMessageBiProcessor<Byte>>) getProcessors(packet);
        if (CollectionUtils.isEmpty(processors)) {
            return Mono.empty();
        }
        Mono<Void> chain = Mono.empty();
        for (AbstractMessageBiProcessor<Byte> messageProcessor : processors) {
            chain = chain.then(Mono.defer(() -> messageProcessor.postProcess(ctx, packet)));
        }
        return chain;
    }

    private List<T> getProcessors(Packet packet) {
        for (ProcessorChain<T> chain : this.processorChains) {
            if (chain.matches(packet)) {
                return chain.getProcessors();
            }
        }
        return null;
    }
}
