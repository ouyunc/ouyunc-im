package com.ouyunc.message.processor.http;

import com.ouyunc.base.constant.HttpRequestConstant;
import com.ouyunc.message.http.HttpRequestProcessor;
import com.ouyunc.message.http.PostHttpRequest;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

/**
 * POST /api/im/push 推送接口：实现 HttpRequestProcessor，类上 @PostHttpRequest 即可。
 * 必须在本方法内写入响应，否则客户端会一直等待导致阻塞。
 */
@PostHttpRequest(HttpRequestConstant.HTTP_PUSH_API_PATH)
public class PushHttpProcessor implements HttpRequestProcessor<Object> {

    /**
     * IM 消息推送处理请求。注意：需鉴权可做统一鉴权；有返回数据时必须调用 HttpUtil.writeJsonResponse 等写入响应。
     *
     * @param ctx     Netty 上下文
     * @param request 完整 HTTP 请求
     */
    @Override
    public Object process(ChannelHandlerContext ctx, FullHttpRequest request) {
        // TODO: 解析 body、鉴权、调用 MessagePushService 并返回 PushResult
        return null;
    }
}
