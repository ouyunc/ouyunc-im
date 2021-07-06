package com.ouyu.im.handler;

import com.ouyu.im.exception.IMException;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.utils.ReaderWriterUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 将非packet 协议类型转成Packet,服务内部只处理packet
 * @Version V1.0
 **/
public class Convert2PacketHandler extends SimpleChannelInboundHandler<Object> {
    private static Logger log = LoggerFactory.getLogger(Convert2PacketHandler.class);



    /**
     * @Author fangzhenxun
     * @Description 类型转换
     * @param ctx
     * @param msg
     * @return void
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        //1.@todo  可以在其他协议这个部分进行封装packet ，然后传到消息处理中, 在这里面可以处理认证与授权的问题，如果没有问题则继续交给后面处理
        //2，ReaderWriterUtil.convertOther2Packet() 或 ReaderWriterUtil.convertPacket2Other()
        try {
            if (msg instanceof BinaryWebSocketFrame) {
                Packet packet = ReaderWriterUtil.readByteBuf2Packet(((BinaryWebSocketFrame) msg).content());
                ctx.fireChannelRead(packet);
            }
            if (msg instanceof Packet) {
                ctx.fireChannelRead(msg);
            }
        }catch (Exception e){
            log.error("协议转换为packet发生异常！");
            throw new IMException("协议转换为packet发生异常！");
        }
    }
}
