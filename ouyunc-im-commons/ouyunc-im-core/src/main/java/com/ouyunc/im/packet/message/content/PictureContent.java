package com.ouyunc.im.packet.message.content;

import java.io.Serializable;

/**
 * 聊天图片内容
 */
public class PictureContent implements Serializable {
    private static final long serialVersionUID = 100012L;
    /**
     * 图片 url
     */
    private String picUrl;

    /**
     * 图片 宽度
     */
    private String picW;

    /**
     * 图片 高度
     */
    private String picH;

    /**
     * mime 类型
     */
    private String mime;

    public String getPicUrl() {
        return picUrl;
    }

    public void setPicUrl(String picUrl) {
        this.picUrl = picUrl;
    }

    public String getPicW() {
        return picW;
    }

    public void setPicW(String picW) {
        this.picW = picW;
    }

    public String getPicH() {
        return picH;
    }

    public void setPicH(String picH) {
        this.picH = picH;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }
}
