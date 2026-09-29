package com.ouyunc.message.protocol;

import com.ouyunc.base.model.Protocol;
import com.ouyunc.base.model.SendCallback;
import com.ouyunc.base.packet.Packet;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

/**
 * @author fzx
 * @description 协议接口
 */
public interface PacketProtocol extends Protocol {


    /**
     * @Author fzx
     * @Description 协议分发器
     * @param ctx
     * @param msg 请求参数
     * @return void
     */
    void doDispatcher(ChannelHandlerContext ctx, Object msg);

    /**
     * 在已经选定的 Channel 上按本协议写出。不查登录表，不做集群选路。
     * 成败只通过 {@code sendCallback} 交回调用方。
     */
    void doSendMessage(Channel channel, Packet packet, SendCallback sendCallback);
}
