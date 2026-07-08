package com.ouyunc.base.packet.message.content;

import java.io.Serial;

/**
 * 图文混合消息内容（{@link com.ouyunc.base.constant.enums.MessageContentTypeEnum#IMAGE_TEXT_CONTENT}）。
 */
public class ImageTextContent extends MediaFileContent {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 与图片一起发送的文字说明 */
    private String text;

    public ImageTextContent() {
    }

    public ImageTextContent(String url, String name, String mime, long size, String text) {
        super(url, name, mime, size);
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
