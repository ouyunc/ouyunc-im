package com.ouyunc.message.handler;

import com.ouyunc.base.constant.MessageConstant;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * 共享的 HTTP 入站解码链：Codec → Chunked → {@link Json413HttpObjectAggregator} → 聚合超长异常处理。
 * {@code Expect: 100-continue} 由 {@link io.netty.handler.codec.http.HttpObjectAggregator}（本处为子类）在
 * {@code newContinueResponse} 中处理，勿再叠加 {@link io.netty.handler.codec.http.HttpServerExpectContinueHandler}，
 * 否则 Netty 文档所述会与聚合器冲突（先无条件 100、去掉 Expect，超长无法在期望阶段拒绝）。
 * <p>
 * 本机端口上首包像 HTTP 的链路（含 WebSocket 握手、MQTT-over-WebSocket、普通 HTTP API）都走同一套
 * 解码与聚合；具体是 WS / MQTT / HTTP 在 {@link com.ouyunc.message.handler.HttpProtocolDispatcherHandler}
 * 里再分支。聚合与 413 JSON 由本类集中拼装，与 {@code HttpProtocolDispatcherProcessor} 解耦；聚合器之后仍保留
 * {@link HttpAggregatorExceptionHandler}，处理 {@link io.netty.handler.codec.http.HttpObjectAggregator} 链路上
 * 其它解码异常（如响应侧超长抛出的 {@link io.netty.handler.codec.http.TooLongHttpContentException}）。
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
                .addLast(MessageConstant.HTTP_OBJECT_AGGREGATOR_HANDLER, new Json413HttpObjectAggregator(maxContentLength))
                .addLast(MessageConstant.HTTP_AGGREGATOR_EXCEPTION_HANDLER, new HttpAggregatorExceptionHandler());
    }
}
