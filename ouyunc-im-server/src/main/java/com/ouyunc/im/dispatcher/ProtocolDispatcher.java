package com.ouyunc.im.dispatcher;


import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.exception.IMException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


/**
 * @Author fangzhenxun
 * @Description: 协议派发器, 具体请看netty 源码例子
 **/
public class ProtocolDispatcher extends ByteToMessageDecoder {
    private static Logger log = LoggerFactory.getLogger(ProtocolDispatcher.class);


    /**
     * @param ctx
     * @param in
     * @param out
     * @return void
     * @Author fangzhenxun
     * @Description 编解码协议分发
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        log.info("ProtocolDispatcher 协议派发器正在处理...");
        // 首先判断缓冲区是否可读
        if (in == null || !in.isReadable()) {
            log.error("缓冲区不可读！");
            throw new IMException("缓冲区不可读！");
        }
        // Will use the first six bytes to detect a protocol.
        if (in.readableBytes() < 6) {
            return;
        }
        // 读索引必须从头开始读的，这样才能保证是第一次读取
        int readerIndex = in.readerIndex();
        if (readerIndex != 0) {
            return;
        }
        // 判断是何种协议,注意这里不可以使用  in.readByte();
        final byte magic1 = in.getByte(readerIndex);
        final byte magic2 = in.getByte(readerIndex + 1);
        // 包含http/https/ws/wss
        if (isHttp(magic1, magic2)) {
            new DispatcherProcessorContext(new HttpDispatcherProcessorStrategy()).process(ctx);
        } else if (isPacket(magic1)) {
            // packet协议中包含多种自定义子协议，比如可以对接jt-818协议，其他硬件或软件自定义协议
            new DispatcherProcessorContext(new PacketDispatcherProcessorStrategy()).process(ctx);
        } else if (isMqtt(in)) {
            // mqtt 原生协议
            new DispatcherProcessorContext(new MqttDispatcherProcessorStrategy()).process(ctx);
        } else {
            // 不知道的协议将关闭
            log.error("正在关闭非法协议！，channelID: {}", ctx.channel().id().asShortText());
            in.clear();
            ctx.close();
        }
    }


    /**
     * @param in
     * @return boolean
     * @Author fangzhenxun
     * @Description 判断是否是mqtt的connect协议
     */
    private boolean isMqtt(ByteBuf in) {
        for (int i = 0; i < 4; i++) {
            final byte magic2 = in.getByte(i + 1);
            // 可变长度所占用字节长度为1个字节
            if (magic2 >= 0 && magic2 <= 127) {
                return determineMqtt(in, i);
            }
        }
        return false;
    }

    /**
     * @param in
     * @param retainLength
     * @return boolean
     * @Author fangzhenxun
     * @Description 选取具体哪个mqtt 以及协议版本
     */
    private boolean determineMqtt(ByteBuf in, int retainLength) {
        final byte MSB = in.getByte(retainLength + 2);
        final byte LSB = in.getByte(retainLength + 3);
        final byte M = in.getByte(retainLength + 4);
        final byte Q = in.getByte(retainLength + 5);
        final byte T_I = in.getByte(retainLength + 6);
        final byte T_s = in.getByte(retainLength + 7);
        // 判断是哪个协议版本,协议可能是3.1.1 / 5.0
        if (MSB == 0 && LSB == 4) {
            if (M == 'M' && Q == 'Q' && T_I == 'T' && T_s == 'T') {
                // 此时是mqtt 协议,且协议版本号为protocolVersion : 4 => 3.1.1, 5 => 5.0
                final byte protocolVersion = in.getByte(retainLength + 8);
                return protocolVersion == 4 || protocolVersion == 5;
            }
        } else if (MSB == 0 && LSB == 6) {
            final byte d = in.getByte(retainLength + 8);
            final byte p = in.getByte(retainLength + 9);
            //协议可能是3.1
            if (M == 'M' && Q == 'Q' && T_I == 'I' && T_s == 's' && d == 'd' && p == 'p') {
                // 此时是mqtt 协议,且协议版本号为protocolVersion : 3 => 3.1
                final byte protocolVersion = in.getByte(retainLength + 10);
                return protocolVersion == 3;
            }
        }
        return false;
    }

    /**
     * @param magic1
     * @return boolean
     * @Author fangzhenxun
     * @Description 判断是否是packet类型协议
     */
    private boolean isPacket(byte magic1) {
        return magic1 == IMConstant.PACKET_MAGIC;
    }

    /**
     * @param magic1
     * @param magic2
     * @return boolean
     * @Author fangzhenxun
     * @Description 判断是否是http类型协议
     */
    private static boolean isHttp(byte magic1, byte magic2) {
        return
                magic1 == 'G' && magic2 == 'E' || // GET
                        magic1 == 'P' && magic2 == 'O' || // POST
                        magic1 == 'P' && magic2 == 'U' || // PUT
                        magic1 == 'H' && magic2 == 'E' || // HEAD
                        magic1 == 'O' && magic2 == 'P' || // OPTIONS
                        magic1 == 'P' && magic2 == 'A' || // PATCH
                        magic1 == 'D' && magic2 == 'E' || // DELETE
                        magic1 == 'T' && magic2 == 'R' || // TRACE
                        magic1 == 'C' && magic2 == 'O';   // CONNECT
    }

}
