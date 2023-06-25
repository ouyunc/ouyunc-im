package com.ouyunc.im.handler;

import com.ouyunc.im.exception.IMException;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.utils.ReaderWriterUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 将非packet 协议类型转成Packet,服务内部只处理packet
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
