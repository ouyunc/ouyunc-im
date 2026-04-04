package com.ouyunc.message.http;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.utils.HttpUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.Map;

/**
 * HTTP 统一前置：鉴权（{@link HttpRequestAuthenticator}）、JSON body 或 {@code multipart/form-data} 解析。
 */
public final class HttpRequestPipeline {

    private HttpRequestPipeline() {
    }

    /**
     * 构建 {@link HttpContext}：按需鉴权、按需反序列化 body（由 {@link HttpRouteDescriptor} 描述）。
     *
     * @param pathVariables 路径模板匹配得到的变量，如 {@code /user/{id}} → id；无模板时传 null 或空 Map
     */
    public static HttpContext prepare(ChannelHandlerContext ctx, FullHttpRequest request, HttpRouteDescriptor descriptor,
                                      Map<String, String> pathVariables) throws HttpPipelineException {
        int aggregatorLimit = HttpContentLengthLimits.aggregatorMaxBytes();
        int readable = request.content().readableBytes();
        if (readable > aggregatorLimit) {
            throw new HttpPipelineException(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, HttpResponseCodeEnum.PAYLOAD_TOO_LARGE,
                    "请求体超过连接允许的最大长度: " + aggregatorLimit + " 字节");
        }

        String rawBody = descriptor.isMultipart() ? "" : HttpUtil.getBodyAsString(request);
        HttpContext httpContext = new HttpContext(ctx, request, rawBody);
        if (pathVariables != null && !pathVariables.isEmpty()) {
            httpContext.setPathVariables(pathVariables);
        }

        if (!descriptor.isIgnoreAuth()) {
            HttpAuthenticators.getGlobal().authenticate(httpContext);
        }

        if (descriptor.isMultipart()) {
            int mpLimit = HttpContentLengthLimits.multipartMaxBytes();
            if (readable > mpLimit) {
                throw new HttpPipelineException(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, HttpResponseCodeEnum.PAYLOAD_TOO_LARGE,
                        "multipart 请求体超过限制: " + mpLimit + " 字节");
            }
            httpContext.setMultipart(HttpMultipartHolder.parse(request, mpLimit));
        } else {
            int generalLimit = HttpContentLengthLimits.maxBytes();
            if (readable > generalLimit) {
                throw new HttpPipelineException(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, HttpResponseCodeEnum.PAYLOAD_TOO_LARGE,
                        "请求体超过限制: " + generalLimit + " 字节");
            }
            Class<?> bodyClass = descriptor.getRequestBodyClass();
            if (bodyClass == null && HttpUtil.isApplicationFormUrlEncoded(request) && StringUtils.isNotBlank(rawBody)) {
                httpContext.setFormUrlEncodedParams(HttpUtil.parseFormUrlEncodedBody(rawBody));
            } else {
                httpContext.setFormUrlEncodedParams(Collections.emptyMap());
            }
            if (bodyClass != null) {
                if (StringUtils.isBlank(rawBody)) {
                    throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST, "请求体不能为空");
                }
                try {
                    Object parsed = JSON.parseObject(rawBody, bodyClass);
                    httpContext.setBody(parsed);
                } catch (Exception e) {
                    throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST, "JSON 解析失败: " + e.getMessage());
                }
            }
        }

        return httpContext;
    }
}
