package com.ouyunc.im.packet.message.content;

import java.io.Serializable;

/**
 * 音频内容
 */
public class AudioContent implements Serializable {
    private static final long serialVersionUID = 100011L;

    /**
     * 音频 url
     */
    private String audioUrl;

    /**
     * 音频标题
     */
    private String audioTitle;

    /**
     * 音频大小
     */
    private String audioSize;

    /**
     * mime 类型
     */
    private String mime;


    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public String getAudioTitle() {
        return audioTitle;
    }

    public void setAudioTitle(String audioTitle) {
        this.audioTitle = audioTitle;
    }

    public String getAudioSize() {
        return audioSize;
    }

    public void setAudioSize(String audioSize) {
        this.audioSize = audioSize;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }
}
