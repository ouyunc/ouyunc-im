package com.ouyunc.message.handler;

import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.convert.PacketConverter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 将非packet 协议类型转成Packet,服务内部只处理packet
 **/
public class Convert2PacketHandler extends SimpleChannelInboundHandler<Object> {
    private static final Logger log = LoggerFactory.getLogger(Convert2PacketHandler.class);

    /**
     * @param ctx
     * @param msg
     * @return void
     * @Author fzx
     * @Description 类型转换
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        for (PacketConverter<?> packetConverter : MessageServerContext.packetConverterList) {
            Packet packet = packetConverter.convertToPacket(ctx, msg);
            if (packet != null) {
                // 交给下个handler处理
                ctx.fireChannelRead(packet);
                return;
            }
        }
        log.error("协议: {} 转换为packet发生异常,暂不支持该协议！", msg);
        throw new MessageException("协议转换为packet发生异常,暂不支持该协议！");
    }



}
