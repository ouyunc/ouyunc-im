package com.ouyu.im.packet.message;

import io.protostuff.Tag;

import java.io.Serializable;

/**
 * @Author fangzhenxun
 * @Description: 群组聊天
 * @Version V1.0
 **/
public class GChatMessage extends Message implements Serializable {


    /**
     * 发送者
     */
    @Tag(1)
    private String from;

    /**
     * 群组唯一标识
     */
    @Tag(2)
    private String to;


    /**
     * 内容类型，如文本(字符串内容，图片路径，音视频路径，文件路径)，图片，音频，视频...，全部以bytes 传输
     */
    @Tag(3)
    private int contentType;


    /**
     * 具体内容
     */
    @Tag(4)
    private byte[] content;

    /**
     * 创建时间戳（毫秒）
     */
    @Tag(5)
    private long  createTime;


    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public int getContentType() {
        return contentType;
    }

    public void setContentType(int contentType) {
        this.contentType = contentType;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
}
