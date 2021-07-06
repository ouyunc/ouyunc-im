package com.ouyu.im.packet.message;

import io.protostuff.Tag;

import java.io.Serializable;

/**
 * @Author fangzhenxun
 * @Description: 推送消息实体分为im与非im：
 * im:添加好友/同意添加/不同意添加/删除好友/添加群组/管理员同意添加/删除群组/踢出群组 等
 * 非im:新闻资讯类/活动推送类/产品推荐类/系统功能类 等
 * @Version V1.0
 **/
public class PushMessage extends Message implements Serializable {


    /**
     * 发送者
     */
    @Tag(1)
    private String from;

    /**
     * 接收者
     */
    @Tag(2)
    private String to;


    /**
     * 内容类型，如文本(字符串内容，图片路径，音视频路径，文件路径)，图片，音频，视频...，
     */
    @Tag(3)
    private int contentType;

    /**
     * 具体内容 friend_add_req, friend_add_req
     */
    @Tag(4)
    private String content;

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }


}
