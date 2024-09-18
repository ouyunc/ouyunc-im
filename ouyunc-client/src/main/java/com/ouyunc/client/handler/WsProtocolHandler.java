package com.ouyunc.client.handler;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;
import com.ouyunc.base.constant.enums.WsMessageContentTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.PacketReaderWriterUtil;
import com.ouyunc.client.listener.event.OnMessageEvent;
import com.ouyunc.core.context.MessageContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.AttributeKey;

import java.net.URI;

/**
 * @author fzx
 * @description websocket 处理
 */
public class WsProtocolHandler extends SimpleChannelInboundHandler<Object> {

    /***
     * @author fzx
     * @description
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 发送握手
        WebSocketClientHandshaker webSocketClientHandshaker = WebSocketClientHandshakerFactory.newHandshaker(new URI("ws://192.168.0.113:8083/ws"), WebSocketVersion.V13, null, true, new DefaultHttpHeaders());
        webSocketClientHandshaker.handshake(ctx.channel());
    }

    /***
     * @author fzx
     * @description websocketFrame 协议帧发送
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 处理握手，握手成功发送登录，登录成功接收消息


//        //     // 该handler 激活的时候发送登录信息
//        //        // @todo 构造登录packet 协议包, 判断是否已经登录
//        //        //     public Message(String from, String to, int contentType, String content, long createTime) {
//        // 接收到登录成功的返回信息进行属性设置以及将该ctx保存起来
//        Packet packet = PacketReaderWriterUtil.readByteBuf2Packet(msg.content());
//        Message message = packet.getMessage();
//        if(message.getContentType() == WsMessageContentTypeEnum.LOGIN_RESPONSE_SUCCESS_CONTENT.getType()){
//            // 登录成功
//            ctx.channel().attr(AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN)).set(ProtocolTypeEnum.WS);
//        }
//        // 然后发布事件（成功事件/接收消息事件），发送消息和接收消息都被解耦
//        MessageContext.publishEvent(new OnMessageEvent(packet), true);
    }
}
