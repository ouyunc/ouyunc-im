package com.ouyunc.message.dispatcher;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * 协议分发处理器：首包识别后安装对应管道。
 * <p>与消息三阶段 {@code BiProcessor} 无关，使用同步 {@link #process}。</p>
 */
public interface ProtocolDispatcherBiProcessor {

    /**
     * 匹配协议；注意不要改变 ByteBuf 读指针。
     */
    boolean match(ByteBuf in);

    /**
     * 安装编解码与业务 Handler，并继续 fire 首包。
     */
    void process(ChannelHandlerContext ctx, ByteBuf in);
}
