package com.ouyunc.base.packet.message.content;

import java.io.Serial;

/**
 * 纯图片消息内容（{@link com.ouyunc.base.constant.enums.MessageContentTypeEnum#IMAGE_CONTENT}）。
 */
public class ImageContent extends MediaFileContent {

    @Serial
    private static final long serialVersionUID = 1L;

    public ImageContent() {
    }

    public ImageContent(String url, String name, String mime, long size) {
        super(url, name, mime, size);
    }
}
