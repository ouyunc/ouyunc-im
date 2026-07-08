package com.ouyunc.base.packet.message.content;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.packet.message.Message;

import java.util.List;

/**
 * 按 {@link MessageContentTypeEnum#getContentClass()} 解析/序列化 message.content。
 */
public final class MessageContents {

    private MessageContents() {
    }

    public static Object parse(String content, MessageContentTypeEnum type) {
        if (type == null || content == null) {
            return content;
        }
        Class<?> clazz = type.getContentClass();
        if (clazz == null || clazz == String.class) {
            return content;
        }
        if (List.class.isAssignableFrom(clazz)) {
            return JSON.parseArray(content);
        }
        return JSON.parseObject(content, clazz);
    }

    public static Object parse(Message message) {
        if (message == null) {
            return null;
        }
        MessageContentTypeEnum type = MessageContentTypeEnum.getByType(message.getContentType());
        return parse(message.getContent(), type);
    }

    public static String toJson(Object content, MessageContentTypeEnum type) {
        if (content == null) {
            return null;
        }
        if (type != null && type.getContentClass() == String.class) {
            return content instanceof String s ? s : String.valueOf(content);
        }
        return JSON.toJSONString(content);
    }

    public static MessageContentTypeEnum resolveType(int contentType) {
        return MessageContentTypeEnum.getByType(contentType);
    }
}
