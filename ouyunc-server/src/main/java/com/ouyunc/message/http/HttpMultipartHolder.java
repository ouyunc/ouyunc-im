package com.ouyunc.message.http;

import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpPostRequestDecoder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * multipart 解析结果：单文件域超过内存阈值时由 Netty 落盘，避免大文件撑爆堆。
 * 须在请求处理结束后调用 {@link #destroy()}（由 {@link HttpRequestDispatcher} 在 {@code finally} 中调用）。
 * 返回后不得再使用 {@link io.netty.handler.codec.http.multipart.FileUpload} 等底层引用（临时文件会被清理）；
 * 若要把上传内容写入 {@link com.ouyunc.base.model.HttpRawResponse}，请先 {@code get()} / 拷贝到自有 {@link io.netty.buffer.ByteBuf}。
 */
public final class HttpMultipartHolder {

    /** 小于该阈值的部件优先在内存，超过则落盘（与 Netty 默认策略一致的量级）。 */
    private static final long MEMORY_THRESHOLD_BYTES = 65536L;

    private final InterfaceHttpPostRequestDecoder decoder;

    private final Map<String, InterfaceHttpData> firstByName;

    private HttpMultipartHolder(InterfaceHttpPostRequestDecoder decoder, Map<String, InterfaceHttpData> firstByName) {
        this.decoder = decoder;
        this.firstByName = firstByName;
    }

    public static HttpMultipartHolder parse(HttpRequest request, long maxBodyBytes) throws HttpPipelineException {
        String ct = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (ct == null || !ct.toLowerCase(Locale.ROOT).trim().startsWith("multipart/")) {
            throw new HttpPipelineException(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE, HttpResponseCodeEnum.UNSUPPORTED_MEDIA_TYPE,
                    "Content-Type 须为 multipart/* ，实际: " + ct);
        }
        DefaultHttpDataFactory factory = new DefaultHttpDataFactory(MEMORY_THRESHOLD_BYTES, StandardCharsets.UTF_8);
        factory.setMaxLimit(maxBodyBytes);
        InterfaceHttpPostRequestDecoder decoder;
        try {
            decoder = new HttpPostRequestDecoder(factory, request);
        } catch (Exception e) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "multipart 解析失败: " + e.getMessage());
        }
        Map<String, InterfaceHttpData> map = new HashMap<>();
        try {
            List<InterfaceHttpData> list = decoder.getBodyHttpDatas();
            for (InterfaceHttpData data : list) {
                map.put(data.getName(), data);
            }
        } catch (Exception e) {
            decoder.destroy();
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "multipart 读取失败: " + e.getMessage());
        }
        return new HttpMultipartHolder(decoder, map);
    }

    public InterfaceHttpData getData(String name) {
        return firstByName.get(name);
    }

    public void destroy() {
        try {
            decoder.destroy();
        } catch (Exception ignored) {
            // ignore
        }
    }
}
