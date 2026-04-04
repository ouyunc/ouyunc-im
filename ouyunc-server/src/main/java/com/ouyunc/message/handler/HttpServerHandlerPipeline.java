package com.ouyunc.message.handler;

import com.ouyunc.base.constant.MessageConstant;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * 共享的 HTTP 入站解码链：Codec → Chunked → {@link HttpObjectAggregator} → 聚合超长异常处理。
 * <p>
 * 本机端口上首包像 HTTP 的链路（含 WebSocket 握手、MQTT-over-WebSocket、普通 HTTP API）都走同一套
 * 解码与聚合；具体是 WS / MQTT / HTTP 在 {@link com.ouyunc.message.handler.HttpProtocolDispatcherHandler}
 * 里再分支。{@link HttpObjectAggregator} 抛出的超长异常必须在进入该分支之前被处理，因此与
 * “协议分发器 Processor” 的职责分离，集中在本类，而非写在 {@code HttpProtocolDispatcherProcessor} 的
 * 流水线拼装细节中。
 */
public final class HttpServerHandlerPipeline {

    private HttpServerHandlerPipeline() {
    }

    /**
     * @param maxContentLength 与 {@link com.ouyunc.message.http.HttpContentLengthLimits#aggregatorMaxBytes()} 一致
     */
    public static void addSharedHttpDecoding(ChannelPipeline pipeline, int maxContentLength) {
        pipeline.addLast(MessageConstant.HTTP_SERVER_CODEC_HANDLER, new HttpServerCodec())
                .addLast(MessageConstant.CHUNKED_WRITE_HANDLER, new ChunkedWriteHandler())
                .addLast(MessageConstant.HTTP_OBJECT_AGGREGATOR_HANDLER, new HttpObjectAggregator(maxContentLength))
                .addLast(MessageConstant.HTTP_AGGREGATOR_EXCEPTION_HANDLER, new HttpAggregatorExceptionHandler());
    }
}
