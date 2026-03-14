package com.ouyunc.base.utils;

import com.alibaba.fastjson2.JSON;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 请求解析小工具：path、query 参数、写 JSON 响应。
 *
 * @author fzx
 */
public final class HttpUtil {

    private HttpUtil() {
    }

    /**
     * 从请求 URI 中取 path（不含 query、fragment）。
     *
     * @param uriStr 完整 URI 或 path，可为 null
     * @return 路径，至少为 "/"
     */
    public static String pathFromUri(String uriStr) {
        if (StringUtils.isBlank(uriStr)) {
            return "/";
        }
        try {
            String path = new URI(uriStr).getPath();
            return StringUtils.isNotBlank(path) ? path : "/";
        } catch (URISyntaxException e) {
            int hash = uriStr.indexOf('#');
            String withoutFragment = hash >= 0 ? uriStr.substring(0, hash) : uriStr;
            int q = withoutFragment.indexOf('?');
            String path = q >= 0 ? withoutFragment.substring(0, q) : withoutFragment;
            return StringUtils.isNotBlank(path) ? path : "/";
        }
    }

    /**
     * 将 query 字符串解析为 key-value 映射，key 与 value 均做 URL 解码。
     * 同 key 多次出现时后者覆盖前者。值可含 "="，按第一个 "=" 切分。
     *
     * @param uriQueryPath query 部分（不含 "?"），可为 null/空
     * @return 解析结果，无参数时返回 null
     */
    public static Map<String, Object> wrapParams2Map(String uriQueryPath) {
        if (StringUtils.isBlank(uriQueryPath)) {
            return null;
        }
        Map<String, Object> result = new HashMap<>();
        for (String pair : uriQueryPath.split("&")) {
            int eq = pair.indexOf('=');
            String key;
            String value;
            if (eq > 0) {
                key = decodeQuery(pair.substring(0, eq).trim());
                value = decodeQuery(pair.substring(eq + 1).trim());
            } else if (eq == 0) {
                continue;
            } else {
                key = decodeQuery(pair.trim());
                value = "";
            }
            if (StringUtils.isNotEmpty(key)) {
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * 从请求 URI 的 query 中取单个参数值（已 URL 解码）。
     *
     * @param request 请求，不可为 null
     * @param name    参数名，区分大小写
     * @return 参数值，不存在或解码异常时返回 null
     */
    public static String getQueryParam(FullHttpRequest request, String name) {
        if (request == null || StringUtils.isBlank(name)) {
            return null;
        }
        String uri = request.uri();
        if (uri == null) {
            return null;
        }
        int q = uri.indexOf('?');
        if (q < 0) {
            return null;
        }
        String query = uri.substring(q + 1);
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq > 0 ? pair.substring(0, eq).trim() : pair.trim();
            if (!name.equals(key)) {
                continue;
            }
            String value = eq > 0 ? pair.substring(eq + 1).trim() : "";
            return decodeQuery(value);
        }
        return null;
    }

    private static String decodeQuery(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    /**
     * 写 JSON 响应。若请求未声明 Keep-Alive 或 request 为 null，则写完后关闭连接。
     *
     * @param ctx     channel 上下文
     * @param request 当前请求，可为 null
     * @param status  响应状态
     * @param body    响应体，将序列化为 JSON
     */
    public static void writeJsonResponse(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status, Object body) {
        byte[] bytes = JSON.toJSONBytes(body);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
        response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8")
                .set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        String connection = request != null ? request.headers().get(HttpHeaderNames.CONNECTION) : null;
        boolean keepAlive = connection != null && "keep-alive".equalsIgnoreCase(connection.trim());
        if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.writeAndFlush(response);
        } else {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
