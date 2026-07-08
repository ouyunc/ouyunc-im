package com.ouyunc.base.packet.message.content;

import java.io.Serial;

/**
 * 语音通话记录（{@link com.ouyunc.base.constant.enums.MessageContentTypeEnum#VOICE_CALL_CONTENT}）。
 */
public class VoiceCallContent extends CallRecordContent {

    @Serial
    private static final long serialVersionUID = 1L;

    public VoiceCallContent() {
    }

    public VoiceCallContent(String status, int duration) {
        super(status, duration);
    }
}
