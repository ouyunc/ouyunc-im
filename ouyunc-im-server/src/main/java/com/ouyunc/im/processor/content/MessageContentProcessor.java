package com.ouyunc.im.processor.content;

import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;

/**
 * 消息内容处理器
 */
public interface MessageContentProcessor {
    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 做逻辑处理
     */
    void doProcess(ChannelHandlerContext ctx, Packet packet);

}
