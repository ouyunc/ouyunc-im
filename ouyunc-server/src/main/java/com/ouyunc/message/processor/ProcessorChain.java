package com.ouyunc.message.processor;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.processor.Processor;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

/**
 * 处理器链
 */
public interface ProcessorChain<T extends Processor<ChannelHandlerContext, Packet>> {

    /**
     * 匹配器
     */
    boolean matches(Packet packet);

    /**
     * 获取处理器
     */
    List<T> getProcessors();
}
