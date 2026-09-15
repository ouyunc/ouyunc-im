package com.ouyunc.message.processor;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.processor.BiProcessor;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

/**
 * 处理器链。
 *
 * @param <T> 链内处理器，须为 {@link BiProcessor}（含泛型返回值）
 */
public interface ProcessorChain<T extends BiProcessor<ChannelHandlerContext, Packet, ?>> {

    /**
     * 匹配器
     */
    boolean matches(Packet packet);

    /**
     * 获取处理器
     */
    List<T> getProcessors();
}
