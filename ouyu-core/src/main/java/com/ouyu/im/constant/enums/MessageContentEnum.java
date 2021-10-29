package com.ouyu.im.constant.enums;

import com.ouyu.im.packet.message.content.ChatContent;
import com.ouyu.im.packet.message.content.LoginContent;

/**
 * @Author fangzhenxun
 * @Description: 消息内容类型枚举
 * @Version V1.0
 **/
public enum MessageContentEnum {

    TEXT_CONTENT(0, String.class, "文本内容类型"),

    LOGIN_CONTENT(1, LoginContent.class, "登录"),

    CHAT_CONTENT(2, ChatContent.class, "聊天");


    /**
     * 唯一标识code
     */
    private int code;
    /**
     * 枚举对应的内容具体类
     */
    private Class<?> contentClass;
    /**
     * 描述
     */
    private String description;

    MessageContentEnum(int code, Class<?> contentClass, String description) {
        this.code = code;
        this.contentClass = contentClass;
        this.description = description;
    }


    public int code() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public Class<?> getContentClass() {
        return contentClass;
    }

    public void setContentClass(Class<?> contentClass) {
        this.contentClass = contentClass;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


    public static MessageContentEnum prototype(int code) {
        for (MessageContentEnum messageContentEnum : MessageContentEnum.values()) {
            if (messageContentEnum.code == code) {
                return messageContentEnum;
            }
        }
        return null;
    }
}
