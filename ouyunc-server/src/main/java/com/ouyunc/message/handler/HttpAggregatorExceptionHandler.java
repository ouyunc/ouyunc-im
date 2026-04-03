package com.ouyunc.message.handler;

import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.model.HttpResponseResult;
import com.ouyunc.base.utils.HttpUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.TooLongHttpContentException;

/**
 * 将 {@link io.netty.handler.codec.http.HttpObjectAggregator} 产生的超长内容异常转为 413 JSON。
 * 与 {@link HttpServerHandlerPipeline} 配套，置于聚合器之后。
 */
public class HttpAggregatorExceptionHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Throwable root = cause;
        if (cause instanceof DecoderException && cause.getCause() != null) {
            root = cause.getCause();
        }
        if (root instanceof TooLongHttpContentException) {
            HttpUtil.writeJsonResponse(ctx, null, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE,
                    HttpResponseResult.fail(HttpResponseCodeEnum.PAYLOAD_TOO_LARGE, "request payload too long"));
            return;
        }
        ctx.fireExceptionCaught(cause);
    }
}
