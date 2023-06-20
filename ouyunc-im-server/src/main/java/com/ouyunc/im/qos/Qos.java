package com.ouyunc.im.qos;

import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;

/**
 * im qos 保证消息可靠性到达
 */
public interface Qos {

    /**
     * qos 前置处理，可以用来对消息去重
     * @return
     */
    default boolean preHandle(ChannelHandlerContext ctx, Packet packet){
        return true;
    }

    /**
     * qos 后置处理,主要对消息发送端进行ack回馈
     */
    default void postHandle(ChannelHandlerContext ctx, Packet packet){
    }

}
