package com.ouyu.im.constant.enums;

/**
 * @Author fangzhenxun
 * @Description: 消息枚举，消息类型 1-文本，2-图片，3-音频，4-视频，5-文档(pdf,doc,xls...)，6-其他
 * @Version V1.0
 **/
public enum MessageContentEnum {
    OTHER(0, "其他"),
    TEXT(1,"文本"),
    PICTURE(2,"图片"),
    AUDIO(3,"音频"),
    video(4,"视频");



    private int contentType;
    private String description;

    MessageContentEnum(int contentType, String description) {
        this.contentType = contentType;
        this.description = description;
    }

    public int getContentType() {
        return contentType;
    }

    public void setContentType(int contentType) {
        this.contentType = contentType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
