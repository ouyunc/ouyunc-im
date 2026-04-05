package com.ouyunc.message.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.model.HttpResponseResult;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpExpectationFailedEvent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.TooLongHttpContentException;
import io.netty.util.AttributeKey;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.EXPECT;

/**
 * 扩展 Netty {@link HttpObjectAggregator}：避免内置无正文 413；请求体过大时对请求侧通过
 * {@link ChannelHandlerContext#fireExceptionCaught(Throwable)} 交给 {@link HttpAggregatorExceptionHandler} 写统一 JSON。
 * <p>
 * {@link #handleOversizedMessage} 对请求侧超长改为 {@link ChannelHandlerContext#fireExceptionCaught(Throwable)}，由
 * {@link HttpAggregatorExceptionHandler} 写 JSON；{@link #newContinueResponse} 中「Expect: 100-continue 且
 * Content-Length 过大」仍直接返回 413 响应帧（由 {@link io.netty.handler.codec.MessageAggregator} 写出，无法再经异常处理器）。
 * <p>
 * 逻辑与 Netty 4.2 {@code HttpObjectAggregator#handleOversizedMessage} / {@code newContinueResponse} 对齐，
 * 仅替换 413 响应的构造方式。
 */
public class Json413HttpObjectAggregator extends HttpObjectAggregator {

    /**
     * 聚合器在触发 {@link TooLongHttpContentException} 前写入，供 {@link HttpAggregatorExceptionHandler} 判断 Keep-Alive 与是否强制关连接。
     */
    public static final AttributeKey<Oversized413Hint> AGGREGATOR_OVERSIZED_HINT =
            AttributeKey.valueOf("ouyunc.AggregatorOversized413Hint");

    public static final class Oversized413Hint {
        public final HttpMessage message;
        public final boolean forceClose;

        public Oversized413Hint(HttpMessage message, boolean forceClose) {
            this.message = message;
            this.forceClose = forceClose;
        }
    }

    private static final FullHttpResponse CONTINUE =
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);
    private static final FullHttpResponse EXPECTATION_FAILED = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.EXPECTATION_FAILED, Unpooled.EMPTY_BUFFER);

    private static final byte[] PAYLOAD_TOO_LARGE_JSON = JSON.toJSONBytes(
            HttpResponseResult.fail(HttpResponseCodeEnum.PAYLOAD_TOO_LARGE, "Request Entity Too Large"),
            JSONWriter.Feature.WriteNulls);

    static {
        EXPECTATION_FAILED.headers().setInt(CONTENT_LENGTH, 0);
    }

    public Json413HttpObjectAggregator(int maxContentLength) {
        super(maxContentLength);
    }

    public Json413HttpObjectAggregator(int maxContentLength, boolean closeOnExpectationFailed) {
        super(maxContentLength, closeOnExpectationFailed);
    }

    @Override
    protected Object newContinueResponse(HttpMessage start, int maxContentLength, ChannelPipeline pipeline) {
        Object response;
        if (unsupportedExpectation(start)) {
            pipeline.fireUserEventTriggered(HttpExpectationFailedEvent.INSTANCE);
            response = EXPECTATION_FAILED.retainedDuplicate();
        } else if (io.netty.handler.codec.http.HttpUtil.is100ContinueExpected(start)) {
            if (!isContentLengthInvalid(start, maxContentLength)) {
                response = CONTINUE.retainedDuplicate();
            } else {
                pipeline.fireUserEventTriggered(HttpExpectationFailedEvent.INSTANCE);
                response = newJson413Response(start).retainedDuplicate();
            }
        } else {
            response = null;
        }
        if (response != null) {
            start.headers().remove(EXPECT);
        }
        return response;
    }

    @Override
    protected void handleOversizedMessage(final ChannelHandlerContext ctx, HttpMessage oversized) throws Exception {
        if (oversized instanceof HttpRequest) {
            // 与父类 HttpObjectAggregator 两段分支等价：true ≈ TOO_LARGE_CLOSE（写完必关），false ≈ TOO_LARGE（可 Keep-Alive）
            boolean forceClose = oversized instanceof FullHttpMessage || !ctx.channel().config().isAutoRead()
                    || !io.netty.handler.codec.http.HttpUtil.is100ContinueExpected(oversized)
                    && !io.netty.handler.codec.http.HttpUtil.isKeepAlive(oversized);
            ctx.channel().attr(AGGREGATOR_OVERSIZED_HINT).set(new Oversized413Hint(oversized, forceClose));
            ctx.fireExceptionCaught(new TooLongHttpContentException("Request entity too large"));
        } else if (oversized instanceof HttpResponse) {
            ctx.close();
            throw new TooLongHttpContentException("Response entity too large: " + oversized);
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * 与 Netty {@code HttpUtil.isUnsupportedExpectation} 等价（该方法在 Netty 4.2 中为包可见，子类无法调用）。
     */
    private static boolean unsupportedExpectation(HttpMessage message) {
        if (!(message instanceof HttpRequest) || message.protocolVersion().compareTo(HttpVersion.HTTP_1_1) < 0) {
            return false;
        }
        CharSequence ev = message.headers().get(HttpHeaderNames.EXPECT);
        return ev != null && !HttpHeaderValues.CONTINUE.toString().equalsIgnoreCase(ev.toString());
    }

    private static FullHttpResponse newJson413Response(HttpMessage start) {
        ByteBuf buf = Unpooled.copiedBuffer(PAYLOAD_TOO_LARGE_JSON);
        DefaultFullHttpResponse resp = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, buf);
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        resp.headers().setInt(CONTENT_LENGTH, PAYLOAD_TOO_LARGE_JSON.length);
        io.netty.handler.codec.http.HttpUtil.setKeepAlive(resp, io.netty.handler.codec.http.HttpUtil.isKeepAlive(start));
        return resp;
    }
}
