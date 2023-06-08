package com.ouyunc.im.packet.message.content;

import java.io.Serializable;

/**
 * 视频
 */
public class VideoContent implements Serializable {
    private static final long serialVersionUID = 100013L;
    /**
     * 视频 url
     */
    private String videoUrl;

    /**
     * 视频封面
     */
    private String poster;

    /**
     * mime 类型
     */
    private String mime;

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getPoster() {
        return poster;
    }

    public void setPoster(String poster) {
        this.poster = poster;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }
}
