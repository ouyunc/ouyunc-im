package com.ouyunc.message.http;

import com.ouyunc.core.processor.Processor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

/**
 * HTTP 请求处理器接口
 */
public interface HttpRequestProcessor extends Processor<ChannelHandlerContext, FullHttpRequest> {
}
