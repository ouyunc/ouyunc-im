package com.ouyunc.base.utils;

import io.netty.handler.codec.http.FullHttpRequest;
import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 请求解析小工具：query 参数等。
 *
 * @author fzx
 */
public final class HttpUtil {

    private HttpUtil() {
    }



    /**
     * 将uri 特殊字符串转成map
     */
    public static Map<String, Object> wrapParams2Map(String uriQueryPath) {
        if (StringUtils.isBlank(uriQueryPath)) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        String[] splitParams = uriQueryPath.split("[&]");
        for (String splitParam : splitParams) {
            String[] paramKeyValue = splitParam.split("[=]");
            //解析出键值
            if (paramKeyValue.length > 1) {
                //正确解析
                result.put(paramKeyValue[0], paramKeyValue[1]);
            } else if (paramKeyValue.length == 1 && StringUtils.isNoneBlank(paramKeyValue[0])) {
                //只有参数没有值，不加入
                result.put(paramKeyValue[0], "");
            }
        }
        return result;
    }

    /**
     * 从请求 URI 的 query 中取参数（已 URL 解码）。
     */
    public static String getQueryParam(FullHttpRequest request, String name) {
        String uri = request.uri();
        int q = uri.indexOf('?');
        if (q < 0) {
            return null;
        }
        for (String pair : uri.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && name.equals(pair.substring(0, eq).trim())) {
                String value = pair.substring(eq + 1).trim();
                try {
                    return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
                } catch (UnsupportedEncodingException e) {
                    return value;
                }
            }
        }
        return null;
    }
}
