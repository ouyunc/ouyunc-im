package com.ouyunc.im.packet.message.content;

import java.io.Serializable;

/**
 * 语音
 */
public class VoiceContent implements Serializable {
    private static final long serialVersionUID = 100014L;

    /**
     * 语音 url
     */
    private String voiceUrl;


    /**
     * 语音总时长，单位秒，最多60秒
     */
    private String voiceDuration;

    /**
     * mime 类型
     */
    private String mime;

    public String getVoiceUrl() {
        return voiceUrl;
    }

    public void setVoiceUrl(String voiceUrl) {
        this.voiceUrl = voiceUrl;
    }

    public String getVoiceDuration() {
        return voiceDuration;
    }

    public void setVoiceDuration(String voiceDuration) {
        this.voiceDuration = voiceDuration;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }
}
