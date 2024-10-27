package com.ouyunc.core.qos;

import com.ouyunc.base.packet.Packet;
import io.netty.channel.ChannelHandlerContext;

/**
 * qos 消息服务质量， 保证消息可达
 */
public interface Qos {

    /**
     * qos 前置处理，可以用来对消息去重
     *
     * @return
     */
    boolean qosPreHandle(ChannelHandlerContext ctx, Packet packet);

    /**
     * qos 后置处理,主要对消息发送端进行ack回馈
     */
    void qosPostHandle(ChannelHandlerContext ctx, Packet packet);
}
