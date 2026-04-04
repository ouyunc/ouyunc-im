package com.ouyunc.message.http;

import com.ouyunc.base.constant.HttpRequestConstant;
import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.message.validator.AppKeyValidator;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;

/**
 * 默认鉴权：仅从请求头 {@link HttpRequestConstant#HTTP_HEADER_APP_KEY} 读取 appKey，经 {@link AppKeyValidator} 校验后写入 {@link HttpContext#setAppKey(String)}。
 */
public class DefaultAppKeyHttpAuthenticator implements HttpRequestAuthenticator {

    @Override
    public void authenticate(HttpContext httpContext) throws HttpPipelineException {
        String appKey = resolveAppKey(httpContext.getRequest());
        if (StringUtils.isBlank(appKey)) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "缺少 appKey（请在请求头设置 " + HttpRequestConstant.HTTP_HEADER_APP_KEY + "）");
        }
        if (!AppKeyValidator.INSTANCE.verify(appKey, httpContext.getChannelContext())) {
            throw new HttpPipelineException(HttpResponseStatus.UNAUTHORIZED, HttpResponseCodeEnum.UNAUTHORIZED,
                    "appKey 无效、已停用或连接数超限");
        }
        httpContext.setAppKey(appKey);
    }

    private static String resolveAppKey(io.netty.handler.codec.http.FullHttpRequest request) {
        String header = request.headers().get(HttpRequestConstant.HTTP_HEADER_APP_KEY);
        return StringUtils.isNotBlank(header) ? header.trim() : null;
    }
}
