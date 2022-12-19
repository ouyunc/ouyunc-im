package com.ouyunc.im.packet.message.content;

import java.io.Serializable;

/**
 * 聊天附件
 */
public class AttachmentContent implements Serializable {
    private static final long serialVersionUID = 100005L;

    /**
     * 其他附件完整网络路径http/https +...
     */
    private String url;

    /**
     * 其他附件名称
     */
    private String name;

    /**
     * 其他附件mime，word/excel/ppt/mp4/
     */
    private String mime;


    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }

}