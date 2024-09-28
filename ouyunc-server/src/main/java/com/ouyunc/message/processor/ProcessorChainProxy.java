package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.Type;
import com.ouyunc.base.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 处理器链代理类
 */
public class ProcessorChainProxy<T extends Processor<Packet>> extends AbstractMessageProcessor<Number> {
    private static final Logger log = LoggerFactory.getLogger(ProcessorChainProxy.class);

    /**
     * 类型标识
     */
    private final Type<? extends Number> type;


    /**
     * 处理器链
     */
    private final List<ProcessorChain<T>> processorChains;

    public ProcessorChainProxy(List<ProcessorChain<T>> processorChains, Type<? extends Number> type) {
        this.type = type;
        this.processorChains = processorChains;
    }


    @Override
    public Type<? extends Number> type() {
        return type;
    }
    /**
     * 消息处理器前置处理
     */
    @SuppressWarnings("unchecked")
    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        List<AbstractMessageProcessor<Byte>> processors = (List<AbstractMessageProcessor<Byte>>) getProcessors(packet);
        if (CollectionUtils.isNotEmpty(processors)) {
            processors.forEach(messageProcessor -> {
                messageProcessor.preProcess(ctx, packet);
            });
        }
    }

    /**
     * 消息处理器
     */
    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        List<T> processors = getProcessors(packet);
        if (CollectionUtils.isNotEmpty(processors)) {
            processors.forEach(processor -> processor.process(ctx, packet));
        }
    }


    /**
     * 消息处理器后置处理
     */
    @SuppressWarnings("unchecked")
    @Override
    public void postProcess(ChannelHandlerContext ctx, Packet packet) {
        List<AbstractMessageProcessor<Byte>> processors = (List<AbstractMessageProcessor<Byte>>) getProcessors(packet);
        if (CollectionUtils.isNotEmpty(processors)) {
            processors.forEach(messageProcessor -> {
                messageProcessor.postProcess(ctx, packet);
            });
        }
    }

    /**
     * 获取processor
     */
    private List<T> getProcessors(Packet packet) {
        for (ProcessorChain<T> chain : this.processorChains) {
            if (chain.matches(packet)) {
                return chain.getProcessors();
            }
        }
        return null;
    }



}
