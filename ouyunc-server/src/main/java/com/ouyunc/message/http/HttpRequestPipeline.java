package com.ouyunc.message.http;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.utils.HttpUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

/**
 * HTTP 统一前置：鉴权（{@link HttpRequestAuthenticator}）、JSON body 或 {@code multipart/form-data} 解析。
 */
public final class HttpRequestPipeline {
    private static final Logger log = LoggerFactory.getLogger(HttpRequestPipeline.class);

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

        final String rawBody;
        if (descriptor.isMultipart()) {
            rawBody = "";
        } else if (descriptor.getRequestBodyClass() != null) {
            // JSON 自字节解析，避免整段 String（UTF-16）分配；原始 JSON 文本见 getBody()
            rawBody = "";
        } else {
            rawBody = HttpUtil.getBodyAsString(request);
        }
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
                log.error("multipart 请求体超过连接允许的最大长度: {} 字节", mpLimit);
                throw new HttpPipelineException(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, HttpResponseCodeEnum.PAYLOAD_TOO_LARGE,
                        "Request Entity Too Large");
            }
            httpContext.setMultipart(HttpMultipartHolder.parse(request, mpLimit));
        } else {
            int generalLimit = HttpContentLengthLimits.maxBytes();
            if (readable > generalLimit) {
                log.error("请求体超过限制: {} 字节", generalLimit);
                throw new HttpPipelineException(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, HttpResponseCodeEnum.PAYLOAD_TOO_LARGE,
                        "Request Entity Too Large");
            }
            Class<?> bodyClass = descriptor.getRequestBodyClass();
            if (bodyClass == null && HttpUtil.isApplicationFormUrlEncoded(request) && StringUtils.isNotBlank(rawBody)) {
                httpContext.setFormUrlEncodedParams(HttpUtil.parseFormUrlEncodedBody(rawBody));
            } else {
                httpContext.setFormUrlEncodedParams(Collections.emptyMap());
            }
            if (bodyClass != null) {
                byte[] bodyBytes = HttpUtil.copyBodyToByteArrayWithoutConsuming(request);
                if (bodyBytes.length == 0) {
                    throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST, "请求体不能为空");
                }
                try {
                    Object parsed = JSON.parseObject(bodyBytes, bodyClass);
                    httpContext.setBody(parsed);
                } catch (Exception e) {
                    throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST, "JSON 解析失败: " + e.getMessage());
                }
            }
        }

        return httpContext;
    }
}
