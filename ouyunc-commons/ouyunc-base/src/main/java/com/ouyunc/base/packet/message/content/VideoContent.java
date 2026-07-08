package com.ouyunc.base.packet.message.content;

import java.io.Serial;

/**
 * 视频消息内容（{@link com.ouyunc.base.constant.enums.MessageContentTypeEnum#VIDEO_CONTENT}）。
 */
public class VideoContent extends MediaFileContent {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 时长（毫秒）；JSON 短字段 {@code d} */
    private int duration;

    public VideoContent() {
    }

    public VideoContent(String url, String name, String mime, long size, int duration) {
        super(url, name, mime, size);
        this.duration = duration;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }
}
