package com.ouyu.im.codec;


import com.ouyu.im.exception.IMException;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.utils.ReaderWriterUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @Author fangzhenxun
 * @Description:
 * @Version V1.0
 **/
public class PacketCodec extends ByteToMessageCodec<Packet<Object>> {
    private static Logger log = LoggerFactory.getLogger(PacketCodec.class);



    /**
     * @Author fangzhenxun
     * @Description 编码
     * @param ctx
     * @param packet
     * @param out
     * @return void
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, Packet<Object> packet, ByteBuf out) throws Exception {
        // 自定义
        if (!verify(packet)) {
            log.error("协议包校验失败");
            throw new IMException("协议包校验失败!");
        }
        ReaderWriterUtil.writePacketInByteBuf(packet, out);
    }



    /**
     * @Author fangzhenxun
     * @Description 校验协议包字段参数 @todo 待完善
     * @param packet
     * @return boolean
     */
    protected boolean verify(Packet<Object> packet) {
        // 校验魔数
        // 校验协议包id
        // 校验设备类型
        // 校验消息类型
        // 校验加密方式
        // 校验序列化方式
        return true;
    }

    /**
     * @Author fangzhenxun
     * @Description 解码，需要处理半包粘包,如果长时间不out出去，则会消息堆积
     * @param ctx
     * @param in
     * @param out
     * @return void
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        out.add(ReaderWriterUtil.readByteBuf2Packet(in));
    }

}
