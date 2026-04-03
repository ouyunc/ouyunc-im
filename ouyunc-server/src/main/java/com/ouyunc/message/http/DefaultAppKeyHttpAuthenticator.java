package com.ouyunc.message.http;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ouyunc.base.constant.HttpRequestConstant;
import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.message.validator.AppKeyValidator;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;

/**
 * 默认鉴权：从 Header {@link HttpRequestConstant#HTTP_HEADER_APP_KEY} 或 JSON 根字段 {@code appKey} 取值，经 {@link AppKeyValidator} 校验后写入 {@link HttpContext#setAppKey(String)}。
 */
public class DefaultAppKeyHttpAuthenticator implements HttpRequestAuthenticator {

    private static final String JSON_APP_KEY = "appKey";

    @Override
    public void authenticate(HttpContext httpContext) throws HttpPipelineException {
        String appKey = resolveAppKey(httpContext.getRequest(), httpContext.getRawBody());
        if (StringUtils.isBlank(appKey)) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "缺少 appKey（Header " + HttpRequestConstant.HTTP_HEADER_APP_KEY + " 或 JSON 字段 appKey）");
        }
        if (!AppKeyValidator.INSTANCE.verify(appKey, httpContext.getChannelContext())) {
            throw new HttpPipelineException(HttpResponseStatus.UNAUTHORIZED, HttpResponseCodeEnum.UNAUTHORIZED,
                    "appKey 无效、已停用或连接数超限");
        }
        httpContext.setAppKey(appKey);
    }

    private static String resolveAppKey(io.netty.handler.codec.http.FullHttpRequest request, String rawBody) {
        String header = request.headers().get(HttpRequestConstant.HTTP_HEADER_APP_KEY);
        if (StringUtils.isNotBlank(header)) {
            return header.trim();
        }
        if (StringUtils.isBlank(rawBody)) {
            return null;
        }
        try {
            JSONObject obj = JSON.parseObject(rawBody);
            if (obj == null) {
                return null;
            }
            String k = obj.getString(JSON_APP_KEY);
            return StringUtils.isNotBlank(k) ? k.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
