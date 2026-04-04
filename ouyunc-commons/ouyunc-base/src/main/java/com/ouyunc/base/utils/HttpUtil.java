package com.ouyunc.base.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import static io.netty.handler.codec.http.HttpUtil.isKeepAlive;
import static io.netty.handler.codec.http.HttpUtil.setContentLength;
import static io.netty.handler.codec.http.HttpUtil.setKeepAlive;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
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
     * 获取文本格式的请求体（最常用，如 JSON/XML/普通文本）
     * @param request FullHttpRequest 对象
     * @return 字符串格式的请求体，空请求体返回空字符串
     */
    public static String getBodyAsString(FullHttpRequest request) {
        // 1. 校验参数，避免空指针
        if (request == null || !request.content().isReadable()) {
            return "";
        }

        // 2. 获取 ByteBuf 并转换为字符串（默认 UTF-8，适配 IM 推送的 JSON 格式）
        ByteBuf content = request.content();


        return content.toString(CharsetUtil.UTF_8);
    }

    /**
     * 获取字节数组格式的请求体（适用于二进制数据，如文件/图片）
     * @param request FullHttpRequest 对象
     * @return 字节数组，空请求体返回空数组
     */
    public static byte[] getBodyAsBytes(FullHttpRequest request) {
        if (request == null || !request.content().isReadable()) {
            return new byte[0];
        }

        ByteBuf content = request.content();
        byte[] bytes = new byte[content.readableBytes()];
        content.readBytes(bytes);

        return bytes;
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

    /**
     * 从请求 URI 的 query 中获取全部参数，以 Map 返回（key、value 均已 URL 解码）。
     * 同 key 多次出现时后者覆盖前者。
     *
     * @param request 请求，可为 null
     * @return 参数 Map，无 query 或 request 为 null 时返回空 Map，不返回 null
     */
    public static Map<String, String> getQueryParams(FullHttpRequest request) {
        if (request == null) {
            return Collections.emptyMap();
        }
        String uri = request.uri();
        if (uri == null) {
            return Collections.emptyMap();
        }
        int q = uri.indexOf('?');
        if (q < 0) {
            return Collections.emptyMap();
        }
        String query = uri.substring(q + 1);
        if (StringUtils.isBlank(query)) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<>();
        for (String pair : query.split("&")) {
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
     * 写 JSON 响应。
     * <ul>
     *   <li>序列化：优先写入 {@link ChannelHandlerContext#alloc()} 分配的 {@link ByteBuf}，减少额外 {@code byte[]} 拷贝（超高 QPS 下更友好）。</li>
     *   <li>Keep-Alive：遵循 Netty {@link io.netty.handler.codec.http.HttpUtil#isKeepAlive(HttpMessage)}（HTTP/1.1 默认持久连接，除非 {@code Connection: close}）。</li>
     * </ul>
     * request 为 null 时按非 Keep-Alive 处理，写完后关闭连接。
     *
     * @param ctx     channel 上下文
     * @param request 当前请求，可为 null
     * @param status  响应状态
     * @param body    响应体，将序列化为 JSON
     */
    public static void writeJsonResponse(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status, Object body) {
        ByteBuf content = ctx.alloc().buffer();
        try {
            try (java.io.OutputStream out = new ByteBufOutputStream(content)) {
                // 默认会省略值为 null 的字段；开启 WriteNulls 后输出 "field":null，避免业务方认为键「丢失」
                JSON.writeTo(out, body, JSONWriter.Feature.WriteNulls);
            }
        } catch (Exception e) {
            content.release();
            throw new RuntimeException("JSON 序列化失败", e);
        }
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        setContentLength(response, content.readableBytes());
        boolean alive = request != null && isKeepAlive(request);
        setKeepAlive(response, alive);
        ChannelFuture future = ctx.writeAndFlush(response);
        if (!alive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }
}
