package com.ouyunc.base.packet.message.content;

import java.io.Serial;

/**
 * 聊天附件消息内容（{@link com.ouyunc.base.constant.enums.MessageContentTypeEnum#FILE_CONTENT}）。
 */
public class ChatFileContent extends MediaFileContent {

    @Serial
    private static final long serialVersionUID = 1L;

    public ChatFileContent() {
    }

    public ChatFileContent(String url, String name, String mime, long size) {
        super(url, name, mime, size);
    }
}
