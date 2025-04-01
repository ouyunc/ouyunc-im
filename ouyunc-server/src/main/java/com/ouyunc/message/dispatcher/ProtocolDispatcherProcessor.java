package com.ouyunc.message.dispatcher;

import com.ouyunc.core.processor.Processor;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * @Author fzx
 * @Description: 协议分发处理器策略
 **/
public interface ProtocolDispatcherProcessor extends Processor<ChannelHandlerContext, ByteBuf> {

    /***
     * @author fzx
     * @description 匹配不同的协议, 注意: 不要改变bytebuf的指针
     */
    boolean match(ByteBuf in);

}
