package com.ouyunc.message.processor;

import com.ouyunc.base.packet.Packet;
import io.netty.channel.ChannelHandlerContext;

/**
 * @Author fzx
 * @Description: 消息处理器接口
 **/
public interface Processor<T> {

    /**
     * @Author fzx
     * @Description 核心业务逻辑处理
     */
    void process(ChannelHandlerContext ctx, T t);

}
