package com.ouyunc.message.handler;

import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.model.HttpResponseResult;
import com.ouyunc.base.utils.HttpUtil;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.TooLongHttpContentException;

/**
 * 将 {@link io.netty.handler.codec.http.HttpObjectAggregator} 链路上的超长与相关解码异常转为统一 JSON。
 * 紧接在 {@link Json413HttpObjectAggregator} 之后；请求侧超长由聚合器 {@code fireExceptionCaught} 转入本类。
 * <p>
 * {@link Json413HttpObjectAggregator#AGGREGATOR_OVERSIZED_HINT} 在异常触发前写入，用于在必须关连接与可
 * Keep-Alive 两种情况下正确调用 {@link HttpUtil#writeJsonResponse}。
 */
public class HttpAggregatorExceptionHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (findInCauseChain(cause, TooLongHttpContentException.class) != null) {
            Json413HttpObjectAggregator.Oversized413Hint hint =
                    ctx.channel().attr(Json413HttpObjectAggregator.AGGREGATOR_OVERSIZED_HINT).getAndSet(null);
            HttpMessage requestForKeepAlive = hint == null || hint.forceClose ? null : hint.message;
            ChannelFuture written = HttpUtil.writeJsonResponse(ctx, requestForKeepAlive,
                    HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE,
                    HttpResponseResult.fail(HttpResponseCodeEnum.PAYLOAD_TOO_LARGE, "Request Entity Too Large"));
            // 对齐父类 else 分支：可 Keep-Alive 时仅在写出失败时关连接
            if (hint != null && !hint.forceClose) {
                written.addListener(f -> {
                    if (!f.isSuccess()) {
                        ctx.close();
                    }
                });
            }
            return;
        }
        if (cause instanceof DecoderException) {
            Throwable leaf = cause;
            while (leaf.getCause() != null && leaf.getCause() != leaf) {
                leaf = leaf.getCause();
            }
            HttpUtil.writeJsonResponse(ctx, null, HttpResponseStatus.BAD_REQUEST,
                    HttpResponseResult.fail(HttpResponseCodeEnum.BAD_REQUEST,
                            leaf.getMessage() != null ? leaf.getMessage() : "HTTP 解码失败"));
            return;
        }
        ctx.fireExceptionCaught(cause);
    }

    private static <T extends Throwable> T findInCauseChain(Throwable t, Class<T> type) {
        while (t != null) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            t = t.getCause();
        }
        return null;
    }
}
