package com.ouyunc.message.dispatcher;

import io.netty.channel.ChannelHandlerContext;

/**
 * @Author fzx
 * @Description: 协议分发处理器策略
 **/
public interface ProtocolDispatcherProcessor<T> {

    /***
     * @author fzx
     * @description 匹配不同的协议, 注意: 不要改变bytebuf的指针
     */
    boolean match(T in);

    /**
     * @Author fzx
     * @Description 处理逻辑
     */
    void process(ChannelHandlerContext ctx);
}
