package com.ouyunc.im.handler;

import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.exception.IMException;
import com.ouyunc.im.log.Log;
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
 **/
public class Convert2PacketHandler extends SimpleChannelInboundHandler<Object> implements Log {
    private static Logger log = LoggerFactory.getLogger(Convert2PacketHandler.class);

    /**
     * 记录该消息包全局traceid(以packetId传递),这里 spanid，以及parentSpanId 暂不做记录，因为packet 路由表中包含整个路由的信息
     * 注意：无论是否是集群还是单机，只要消息到达该处理转换类，就证明这个消息时第一次到达该服务实例，所以不会出现MDC中已经记录过traceId(如果有则是bug)
     */
    @Override
    public void log(Packet packet) {
        MDC.put(IMConstant.LOG_TRACE_ID, String.valueOf(packet.getPacketId()));
    }

    /**
     * 清理日志记录
     */
    @Override
    public void clear() {
        MDC.clear();
    }

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
            Packet packet = null;
            if (msg instanceof BinaryWebSocketFrame) {
                packet = ReaderWriterUtil.readByteBuf2Packet(((BinaryWebSocketFrame) msg).content());
            }else if (msg instanceof Packet) {
                // 集群投递消息时会走这里
                packet = (Packet) msg;
            }
            // 记录日志
            log(packet);
            ctx.fireChannelRead(packet);
        }catch (Exception e){
            log.error("协议转换为packet发生异常！");
            throw new IMException("协议转换为packet发生异常！");
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        super.channelReadComplete(ctx);
        // 清理日志
        clear();
    }
}
