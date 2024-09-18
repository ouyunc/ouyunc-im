package com.ouyunc.message.protocol;

import com.ouyunc.base.model.Protocol;
import com.ouyunc.base.model.SendCallback;
import com.ouyunc.base.packet.Packet;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;

/**
 * @author fzx
 * @description 协议接口
 */
public interface PacketProtocol extends Protocol {


    /**
     * @Author fzx
     * @Description 协议分发器
     * @param ctx
     * @param queryParamsMap 请求参数
     * @return void
     */
    void doDispatcher(ChannelHandlerContext ctx, Map<String, Object> queryParamsMap);

    /**
     * @Author fzx
     * @Description
     * @param packet 消息包
     * @param to 接受者
     * @param sendCallback 这个发送的回调，针对成功来说，只是理论上的成功，因为writeAndFlush 本身就是异步的，加上网络的不稳定性，很难严格意义上的判断发送成功
     */
    void doSendMessage(Packet packet, String to, SendCallback sendCallback);
}
