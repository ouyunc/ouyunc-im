package com.ouyu.im.packet.message.content;


import java.io.Serializable;
import java.util.List;

/**
 * @Author fangzhenxun
 * @Description: im聊天业务消息内容格式
 * @Version V1.0
 **/
public class ChatContent implements Serializable {
    private static final long serialVersionUID = -4827508785475560873L;

    /**
     * 文本
     */
    private String text;

    /**
     * 其他附件
     */
    private List<Attachment> attachments;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
    }

    public static class Attachment implements Serializable{
        private static final long serialVersionUID = 7351575184358973336L;
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
        /**
         * 插入文本的位置，该位置相对文本的长度
         */
        private int position;



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

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }
    }
}
