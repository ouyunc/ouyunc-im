package com.ouyunc.base.packet.message.content;

import java.io.Serial;
import java.io.Serializable;

/**
 * 明信片/贺卡消息内容（{@link com.ouyunc.base.constant.enums.MessageContentTypeEnum#POSTCARD_CONTENT}）。
 */
public class PostcardContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 运营侧模板 ID；有模板时可只传模板 + 变量，客户端按模板渲染 */
    private String templateId;
    /** 封面/背景图 URL */
    private String coverUrl;
    /** 卡片主标题，如「新春快乐」 */
    private String title;
    /** 正文或祝福语 */
    private String text;
    /** 主题分类标识，如 festival / birthday，便于客户端换肤或统计 */
    private String theme;
    /** 点击卡片跳转的 H5 / DeepLink（可选） */
    private String link;

    public PostcardContent() {
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }
}
