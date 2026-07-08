package com.ouyunc.base.packet.message.content;

import java.io.Serial;
import java.io.Serializable;

/**
 * HTTP 上传后的媒体文件引用（图片/附件/语音/视频共用字段）。
 * <p>
 * 线上 JSON 兼容短字段 {@code n}/{@code m}/{@code s} 与完整字段 {@code name}/{@code mime}/{@code size}。
 */
public class MediaFileContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 可访问的相对或绝对 URL */
    private String url;

    private String name;

    private String mime;

    private long size;

    public MediaFileContent() {
    }

    public MediaFileContent(String url, String name, String mime, long size) {
        this.url = url;
        this.name = name;
        this.mime = mime;
        this.size = size;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
