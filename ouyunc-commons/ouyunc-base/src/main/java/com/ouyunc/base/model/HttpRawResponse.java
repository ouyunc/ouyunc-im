package com.ouyunc.base.model;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.nio.charset.StandardCharsets;

/**
 * 非 JSON 的原始二进制响应：由 HTTP 分发器直接写出，不包 {@link HttpResponseResult}。
 * <p>
 * 性能说明：{@code body} 建议使用 {@link io.netty.buffer.Unpooled#wrappedBuffer(byte[])} 或
 * {@link io.netty.channel.ChannelHandlerContext#alloc()} 分配的堆外/堆内缓冲；写完后若
 * {@link #isReleaseBodyAfterWrite()} 为 true，会在 flush 完成时 {@link ByteBuf#release()}。
 */
public final class HttpRawResponse {

    private final HttpResponseStatus status;
    private final ByteBuf body;
    private final String contentType;
    private final String contentDisposition;
    private final boolean releaseBodyAfterWrite;

    public HttpRawResponse(HttpResponseStatus status, ByteBuf body, String contentType, String contentDisposition,
                           boolean releaseBodyAfterWrite) {
        this.status = status == null ? HttpResponseStatus.OK : status;
        this.body = body;
        this.contentType = contentType;
        this.contentDisposition = contentDisposition;
        this.releaseBodyAfterWrite = releaseBodyAfterWrite;
    }

    public static HttpRawResponse of(ByteBuf body, String contentType) {
        return new HttpRawResponse(HttpResponseStatus.OK, body, contentType, null, true);
    }

    /**
     * {@code body} 为 UTF-8 文本（仍走原始响应，不经过 JSON 包装）。
     */
    public static HttpRawResponse utf8Text(String text) {
        byte[] bytes = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        return new HttpRawResponse(HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(bytes),
                "text/plain; charset=UTF-8", null, true);
    }

    public HttpResponseStatus getStatus() {
        return status;
    }

    public ByteBuf getBody() {
        return body;
    }

    public String getContentType() {
        return contentType;
    }

    public String getContentDisposition() {
        return contentDisposition;
    }

    public boolean isReleaseBodyAfterWrite() {
        return releaseBodyAfterWrite;
    }
}
