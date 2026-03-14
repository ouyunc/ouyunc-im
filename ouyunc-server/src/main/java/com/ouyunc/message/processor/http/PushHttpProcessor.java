package com.ouyunc.message.processor.http;

import com.ouyunc.base.constant.HttpRequestConstant;
import com.ouyunc.message.http.HttpRequestProcessor;
import com.ouyunc.message.http.PostHttpRequest;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * POST /api/im/push 推送接口：实现 HttpRequestProcessor，类上 @PostMapping 即可。
 */
@PostHttpRequest(HttpRequestConstant.HTTP_PUSH_API_PATH)
public class PushHttpProcessor implements HttpRequestProcessor {
    private static final Logger log = LoggerFactory.getLogger(PushHttpProcessor.class);


    /**
     * IM消息推送处理请求,注意需要进行鉴权，可考虑统一鉴权,最后如果又返回数据，需要自行返回
     */
    @Override
    public void process(ChannelHandlerContext ctx, FullHttpRequest request) {
        // do something
    }
}
