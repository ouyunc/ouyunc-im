package com.ouyunc.base.packet.message.content;

import java.io.Serial;

/**
 * 视频通话记录（{@link com.ouyunc.base.constant.enums.MessageContentTypeEnum#VIDEO_CALL_CONTENT}）。
 */
public class VideoCallContent extends CallRecordContent {

    @Serial
    private static final long serialVersionUID = 1L;

    public VideoCallContent() {
    }

    public VideoCallContent(String status, int duration) {
        super(status, duration);
    }
}
