package com.ouyunc.message.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

/**
 * HTTP 请求处理器接口
 */
public interface HttpRequestProcessor<R>{
    /**
     * @Author fzx
     * @Description 核心业务逻辑处理
     */
    R process(ChannelHandlerContext context, FullHttpRequest request) throws Exception;
}
