package com.ouyunc.message.handler;

import com.ouyunc.base.constant.HttpRequestConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.message.http.HttpRequestAdmission;
import com.ouyunc.message.http.HttpRequestDispatcher;
import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.model.HttpResponseResult;
import com.alibaba.fastjson2.JSON;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 每条连接独立的聚合前准入器，计数由节点共享。正文分块进入聚合器之前计费；
 * 完整请求同步传递至分发器时转移所有权，其他路径在本轮传播结束后释放。
 * 接收超时、断连和协议升级均回收额度，不持有已交给业务的 Lease。
 */
public final class HttpReceiveAdmissionHandler extends ChannelInboundHandlerAdapter {
    private static final byte[] BUSY_RESPONSE = JSON.toJSONBytes(HttpResponseResult.error(
            HttpResponseCodeEnum.SERVICE_UNAVAILABLE, "HTTP capacity exhausted or service stopping"));
    private HttpRequestAdmission.Lease lease;
    private ScheduledFuture<?> deadline;
    private boolean rejecting;

    /** 只在当前 EventLoop 上取走当前请求额度；支持同连接顺序到达的多个请求。 */
    public static HttpRequestAdmission.Lease take(ChannelHandlerContext ctx) {
        HttpReceiveAdmissionHandler handler = ctx.pipeline().get(HttpReceiveAdmissionHandler.class);
        if (handler == null) {
            return null;
        }
        HttpRequestAdmission.Lease result = handler.lease;
        handler.lease = null;
        return result;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (rejecting) {
            ReferenceCountUtil.release(msg);
            return;
        }
        if (msg instanceof HttpRequest request) {
            finish();
            // 仅确定无正文的探活免配额；chunked 或带正文请求必须正常计费。
            if (!isProbe(request)) {
                if (HttpRequestDispatcher.isStopping()) {
                    reject(ctx, msg, HttpResponseStatus.SERVICE_UNAVAILABLE);
                    return;
                }
                lease = HttpRequestAdmission.tryAcquire(MessageConstant.HTTP_REQUEST_OVERHEAD_BYTES);
                if (lease == null) {
                    reject(ctx, msg, HttpResponseStatus.SERVICE_UNAVAILABLE);
                    return;
                }
                deadline = ctx.executor().schedule(() -> {
                    rejecting = true;
                    finish();
                    ctx.close();
                }, MessageConstant.HTTP_RECEIVE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            }
        }
        if (msg instanceof HttpContent content && lease != null
                && !lease.addBytes(content.content().capacity())) {
            reject(ctx, msg, HttpResponseStatus.SERVICE_UNAVAILABLE);
            return;
        }
        boolean last = msg instanceof LastHttpContent;
        try {
            ctx.fireChannelRead(msg);
        } finally {
            if (last) {
                finish();
            }
        }
    }

    private static boolean isProbe(HttpRequest request) {
        String path = request.uri();
        return HttpMethod.GET.equals(request.method())
                && !HttpUtil.isTransferEncodingChunked(request)
                && HttpUtil.getContentLength(request, 0) == 0
                && (HttpRequestConstant.HTTP_HEALTH_PATH.equals(path)
                || HttpRequestConstant.HTTP_READY_PATH.equals(path)
                || HttpRequestConstant.HTTP_WRITE_READY_PATH.equals(path));
    }

    /** 已拒绝的连接不再复用，防止后续正文被当成下一条请求。 */
    private void reject(ChannelHandlerContext ctx, Object msg, HttpResponseStatus status) {
        rejecting = true;
        ReferenceCountUtil.release(msg);
        finish();
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                Unpooled.wrappedBuffer(BUSY_RESPONSE));
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, BUSY_RESPONSE.length);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void finish() {
        if (deadline != null) {
            deadline.cancel(false);
            deadline = null;
        }
        if (lease != null) {
            lease.close();
            lease = null;
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        finish();
        super.channelInactive(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        finish();
    }
}
