package com.ouyunc.message.dispatcher;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.message.handler.HttpProtocolDispatcherHandler;
import com.ouyunc.message.handler.EphemeralRemoteClientRealIpHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fzx
 * @description http 协议
 */
public class HttpProtocolDispatcherProcessor implements ProtocolDispatcherProcessor {
    private static final Logger log = LoggerFactory.getLogger(HttpProtocolDispatcherProcessor.class);

    @Override
    public boolean match(ByteBuf in) {
        // 判断是何种协议,注意这里不可以使用  in.readByte();
        final byte magic1 = in.getByte(MessageConstant.ZERO);
        final byte magic2 = in.getByte(MessageConstant.ZERO + 1);
        return isHttp(magic1, magic2);
    }

    /**
     * @Author fzx
     * @Description 处理该协议对应的handler
     */
    @Override
    public void process(ChannelHandlerContext ctx, ByteBuf in) {
        ctx.pipeline()
                .addLast(MessageConstant.HTTP_SERVER_CODEC_HANDLER, new HttpServerCodec())
                .addLast(MessageConstant.CHUNKED_WRITE_HANDLER, new ChunkedWriteHandler())
                .addLast(MessageConstant.HTTP_OBJECT_AGGREGATOR_HANDLER, new HttpObjectAggregator(Integer.MAX_VALUE))
                .addLast(MessageConstant.REMOTE_CLIENT_REAL_IP_HANDLER, new EphemeralRemoteClientRealIpHandler())
                // 这一步没有加自定义编解码器，是因为上面的处理器已经处理了消息编解码
                // http 协议分发处理器
                .addLast(MessageConstant.HTTP_DISPATCHER_HANDLER, new HttpProtocolDispatcherHandler());
        // 移除协议分发器，如果不移除在处理业务消息时还是会进行消息分发处理
        ctx.pipeline().remove(MessageConstant.PROTOCOL_DISPATCHER_HANDLER);
        // 调用下一个handle的active
        ctx.fireChannelActive();
    }


    /**
     * @Author fzx
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
