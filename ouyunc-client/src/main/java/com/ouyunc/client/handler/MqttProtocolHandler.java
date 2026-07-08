package com.ouyunc.client.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.mqtt.MqttMessage;

/**
 * @author fzx
 * @description mqtt 协议处理器
 */
public class MqttProtocolHandler extends SimpleChannelInboundHandler<MqttMessage> {

    /***
     * @author fzx
     * @description mqtt 协议处理器
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) throws Exception {

    }
}
