package com.ouyunc.base.model;

import io.netty.handler.codec.http.HttpResponseStatus;

import java.nio.file.Path;

/**
 * 文件下载响应：使用 Netty {@link io.netty.channel.DefaultFileRegion} 零拷贝发送（在支持 sendfile 的传输上由内核优化）。
 * 不将整个文件读入 JVM 堆。
 */
public final class HttpFileResponse {

    private final HttpResponseStatus status;
    private final Path path;
    private final String contentType;
    private final String downloadFileName;

    public HttpFileResponse(HttpResponseStatus status, Path path, String contentType, String downloadFileName) {
        this.status = status == null ? HttpResponseStatus.OK : status;
        this.path = path;
        this.contentType = contentType;
        this.downloadFileName = downloadFileName;
    }

    public static HttpFileResponse attachment(Path path, String downloadFileName) {
        return new HttpFileResponse(HttpResponseStatus.OK, path,
                "application/octet-stream", downloadFileName);
    }

    public HttpResponseStatus getStatus() {
        return status;
    }

    public Path getPath() {
        return path;
    }

    public String getContentType() {
        return contentType;
    }

    public String getDownloadFileName() {
        return downloadFileName;
    }
}
