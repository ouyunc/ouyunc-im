package com.ouyunc.im.handler;

import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.exception.IMException;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.utils.ReaderWriterUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * @Author fangzhenxun
 * @Description: 将非packet 协议类型转成Packet,服务内部只处理packet
 * @Version V3.0
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
        Packet packet = null;
        if (msg instanceof BinaryWebSocketFrame) {
            packet = ReaderWriterUtil.readByteBuf2Packet(((BinaryWebSocketFrame) msg).content());
        }
        if (msg instanceof Packet) {
            packet = (Packet) msg;
        }
        if (packet != null) {
            MDC.put(IMConstant.LOG_TRACE_ID, String.valueOf(packet.getPacketId()));
            log.info("消息包转换为：{}", packet);
            ctx.fireChannelRead(packet);
        }else {
            throw new IMException("协议转换为packet发生异常,暂不支持该协议包！");
        }
    }
}
