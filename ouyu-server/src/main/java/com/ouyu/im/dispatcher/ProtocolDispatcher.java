package com.ouyu.im.dispatcher;

import com.ouyu.im.constant.ImConstant;
import com.ouyu.im.designpattern.strategy.dispatcher.DispatcherProcessorContext;
import com.ouyu.im.designpattern.strategy.dispatcher.HttpDispatcherProcessorStrategy;
import com.ouyu.im.designpattern.strategy.dispatcher.PacketDispatcherProcessorStrategy;
import com.ouyu.im.exception.IMException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


/**
 * @Author fangzhenxun
 * @Description: 协议派发器, 具体请看netty 源码例子
 * @Version V1.0
 **/
public class ProtocolDispatcher extends ByteToMessageDecoder {
    private static Logger log = LoggerFactory.getLogger(ProtocolDispatcher.class);



    /**
     * @Author fangzhenxun
     * @Description 编解码协议分发
     * @param ctx
     * @param in
     * @param out
     * @return void
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 首先判断缓冲区是否可读
        if (in == null || !in.isReadable()) {
            log.error("缓冲区不可读！");
            throw new IMException("缓冲区不可读！");
        }
        // Will use the first five bytes to detect a protocol.
        if (in.readableBytes() < 5) {
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
        }else if(isPacket(magic1)){
            // packet协议中包含多种自定义子协议，比如可以对接jt-818协议，其他硬件或软件自定义协议
            new DispatcherProcessorContext(new PacketDispatcherProcessorStrategy()).process(ctx);
        }else {
            // 不知道的协议将关闭
            log.error("正在关闭非法协议！，channelID: {}", ctx.channel().id().asShortText());
            in.clear();
            ctx.close();
        }
    }



    /**
     * @Author fangzhenxun
     * @Description 判断是否是packet类型协议
     * @param magic1
     * @return boolean
     */
    private boolean isPacket(byte magic1) {
        return magic1 == ImConstant.PACKET_MAGIC;
    }

    /**
     * @Author fangzhenxun
     * @Description 判断是否是http类型协议
     * @param magic1
     * @param magic2
     * @return boolean
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
