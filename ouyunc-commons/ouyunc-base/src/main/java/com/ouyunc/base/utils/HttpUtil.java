package com.ouyunc.base.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.model.HttpFileResponse;
import com.ouyunc.base.model.HttpRawResponse;
import com.ouyunc.base.model.HttpResponseResult;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import static io.netty.handler.codec.http.HttpUtil.isKeepAlive;
import static io.netty.handler.codec.http.HttpUtil.setContentLength;
import static io.netty.handler.codec.http.HttpUtil.setKeepAlive;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
     * 拷贝请求体为 UTF-8 字节数组，不移动 {@link ByteBuf#readerIndex()}，可供多次读取或 JSON 直接从字节解析。
     */
    public static byte[] copyBodyToByteArrayWithoutConsuming(FullHttpRequest request) {
        if (request == null || !request.content().isReadable()) {
            return new byte[0];
        }
        ByteBuf content = request.content();
        int n = content.readableBytes();
        byte[] bytes = new byte[n];
        content.getBytes(content.readerIndex(), bytes);
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
     * 是否为 {@code application/x-www-form-urlencoded}（忽略 charset 等后缀参数）。
     */
    public static boolean isApplicationFormUrlEncoded(FullHttpRequest request) {
        if (request == null) {
            return false;
        }
        String ct = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (ct == null) {
            return false;
        }
        String s = ct.trim().toLowerCase(Locale.ROOT);
        int semi = s.indexOf(';');
        if (semi > 0) {
            s = s.substring(0, semi).trim();
        }
        return "application/x-www-form-urlencoded".equals(s);
    }

    /**
     * 解析 URL 编码的表单体（与 query 串格式相同）；同名字段多次出现时取<strong>最后一次</strong>。
     */
    public static Map<String, String> parseFormUrlEncodedBody(String rawBody) {
        if (StringUtils.isBlank(rawBody)) {
            return Collections.emptyMap();
        }
        QueryStringDecoder decoder = new QueryStringDecoder("?" + rawBody, StandardCharsets.UTF_8, false);
        Map<String, List<String>> src = decoder.parameters();
        if (src.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : src.entrySet()) {
            List<String> list = e.getValue();
            if (list != null && !list.isEmpty()) {
                out.put(e.getKey(), list.get(list.size() - 1));
            }
        }
        return out;
    }

    /**
     * 合并 query 与表单：先查 URI query，没有再查 {@code application/x-www-form-urlencoded} 解析表。
     */
    public static String getRequestParam(FullHttpRequest request, String name, Map<String, String> formBody) {
        String q = getQueryParam(request, name);
        if (q != null) {
            return q;
        }
        if (formBody == null || formBody.isEmpty()) {
            return null;
        }
        return formBody.get(name);
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
     * @param request 当前请求（任意 {@link HttpMessage}，一般为 {@link FullHttpRequest}），可为 null
     * @param status  响应状态
     * @param body    响应体，将序列化为 JSON
     * @return 写出 {@link FullHttpResponse} 的 future，便于调用方追加失败时关闭等监听
     */
    public static ChannelFuture writeJsonResponse(ChannelHandlerContext ctx, HttpMessage request, HttpResponseStatus status, Object body) {
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
        return future;
    }

    /**
     * 写出 {@link HttpRawResponse}：单帧 {@link FullHttpResponse}，适合已聚合在内存/缓冲中的负载。
     */
    public static void writeRawResponse(ChannelHandlerContext ctx, FullHttpRequest request, HttpRawResponse raw) {
        ByteBuf body = raw.getBody();
        if (body == null) {
            body = Unpooled.EMPTY_BUFFER;
        } else if (!raw.isReleaseBodyAfterWrite()) {
            body.retain();
        }
        String ct = StringUtils.isNotBlank(raw.getContentType()) ? raw.getContentType() : "application/octet-stream";
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, raw.getStatus(), body);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, ct);
        if (StringUtils.isNotBlank(raw.getContentDisposition())) {
            response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, raw.getContentDisposition());
        }
        setContentLength(response, body.readableBytes());
        boolean alive = request != null && isKeepAlive(request);
        setKeepAlive(response, alive);
        ChannelFuture future = ctx.writeAndFlush(response);
        if (!alive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * 文件下载：{@link DefaultFileRegion} + {@link LastHttpContent}，大文件不占用与文件等大的 JVM 堆。
     */
    public static void writeFileResponse(ChannelHandlerContext ctx, FullHttpRequest request, HttpFileResponse fileResponse) throws IOException {
        var path = fileResponse.getPath();
        if (path == null || !Files.isRegularFile(path)) {
            writeJsonResponse(ctx, request, HttpResponseStatus.NOT_FOUND,
                    HttpResponseResult.fail(HttpResponseCodeEnum.NOT_FOUND));
            return;
        }
        long len = Files.size(path);
        DefaultFileRegion region = new DefaultFileRegion(path.toFile(), 0, len);

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, fileResponse.getStatus());
        String ct = StringUtils.isNotBlank(fileResponse.getContentType())
                ? fileResponse.getContentType() : "application/octet-stream";
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, ct);
        if (StringUtils.isNotBlank(fileResponse.getDownloadFileName())) {
            String fn = fileResponse.getDownloadFileName().replace("\"", "'");
            response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + fn + "\"");
        }
        setContentLength(response, len);
        boolean alive = request != null && isKeepAlive(request);
        setKeepAlive(response, alive);

        ctx.write(response);
        ctx.write(region);
        ChannelFuture last = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        last.addListener((ChannelFuture f) -> {
            // DefaultFileRegion 由 Netty 写出路径在传输结束后自行 release，不可在此处再次 release（会 refCnt:0 异常）
            if (!alive) {
                ctx.close();
            }
        });
    }
}
