package com.ouyunc.im.dispatcher;

import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.handler.HttpProtocolDispatcherHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * @Author fangzhenxun
 * @Description: http 调度处理器
 **/
public class HttpDispatcherProcessorStrategy implements DispatcherProcessorStrategy{


    @Override
    public void doProcess(ChannelHandlerContext ctx) {
        ctx.pipeline()
                .addLast(IMConstant.HTTP_SERVER_CODEC, new HttpServerCodec())
                .addLast(IMConstant.CHUNKED_WRITE_HANDLER, new ChunkedWriteHandler())
                .addLast(IMConstant.HTTP_OBJECT_AGGREGATOR, new HttpObjectAggregator(Integer.MAX_VALUE))
                // 这一步没有加自定义编解码器，是因为上面的处理器已经处理了消息编解码
                // http 协议分发处理器
                .addLast(IMConstant.HTTP_DISPATCHER_HANDLER, new HttpProtocolDispatcherHandler());

        // 移除协议分发器，如果不移除在处理业务消息时还是会进行消息分发处理
        ctx.pipeline().remove(IMConstant.PROTOCOL_DISPATCHER);
        // 调用下一个handle的active
        ctx.fireChannelActive();
    }
}
