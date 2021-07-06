package com.ouyu.im.designpattern.strategy.dispatcher;

import com.ouyu.im.constant.ImConstant;
import com.ouyu.im.handler.HttpProtocolDispatcherHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * @Author fangzhenxun
 * @Description: http 调度处理器
 * @Version V1.0
 **/
public class HttpDispatcherProcessorStrategy implements DispatcherProcessorStrategy{


    @Override
    public void doProcess(ChannelHandlerContext ctx) {
        ctx.pipeline()
                .addLast(ImConstant.LOG, new LoggingHandler(LogLevel.INFO))
                .addLast(ImConstant.HTTP_SERVER_CODEC, new HttpServerCodec())
                .addLast(ImConstant.CHUNKED_WRITE_HANDLER, new ChunkedWriteHandler())
                .addLast(ImConstant.HTTP_OBJECT_AGGREGATOR, new HttpObjectAggregator(Integer.MAX_VALUE))
                // http处理
                .addLast(ImConstant.HTTP_DISPATCHER_HANDLER, new HttpProtocolDispatcherHandler());

        // 移除协议分发器
        ctx.pipeline().remove(ImConstant.PROTOCOL_DISPATCHER);
        // 调用下一个handle的active
        ctx.fireChannelActive();
    }
}
