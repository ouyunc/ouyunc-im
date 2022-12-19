package com.ouyunc.im.constant.enums;

import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.content.*;

/**
 * @Author fangzhenxun
 * @Description: 消息内容类型枚举
 * @Version V3.0
 **/
public enum MessageContentEnum {


    SYN_CONTENT(1, String.class, "内部消息心跳syn消息内容"),
    ACK_CONTENT(2, String.class, "内部消息心跳ack消息内容"),
    PING_CONTENT(3, String.class, "外部消息心跳ping消息内容"),
    PONG_CONTENT(4, String.class, "外部消息心跳pong消息内容"),
    LOGIN_CONTENT(5, LoginContent.class, "外部客户端登录消息内容"),
    CLIENT_REPLY_ACK_CONTENT(6, ClientReplyAckContent.class, "外部客户端收到消息后的回信内容"),
    SERVER_REPLY_ACK_CONTENT(7, Packet.class, "服务端收到消息后给客户端的回信内容"),
    SERVER_WARNING_CONTENT(8, ServerWarningContent.class, "服务端发出的警告内容"),
    READ_RECEIPT_CONTENT(9, ReadReceiptContent.class, "已读回执消息内容"),
    OFFLINE_CONTENT(10, OfflineContent.class, "离线消息内容"),



    FRIEND_JOIN(11, String.class, "添加好友请求"),
    FRIEND_REFUSE(12, String.class, "拒绝好友请求"),
    FRIEND_AGREE(13, String.class, "同意好友请求"),
    FRIEND_DELETE(14, String.class, "删除好友"),
    FRIEND_JOIN_BLACKLIST(15, String.class, "将好友加入黑名单"),
    FRIEND_EXIT_BLACKLIST(16, String.class, "将好友退出黑名单"),

    GROUP_JOIN(21, String.class, "加群请求"),
    GROUP_REFUSE(22, String.class, "管理员/群主 拒绝加群申请"),
    GROUP_AGREE(23, String.class, "管理员/群主 同意加群申请"),
    GROUP_DISBAND(24, String.class, "群主 解散群"),
    GROUP_KICK(25, String.class, "踢出群"),
    GROUP_EXIT(26, String.class, "退出群"),
    GROUP_SHIELD(27, String.class, "屏蔽群"),
    GROUP_PUBLISH_ANNOUNCEMENT(28, String.class, "发布群公告"),


    CHAT_TEXT_CONTENT(50, String.class, "聊天文本内容类型"),
    CHAT_ATTACHMENT_CONTENT(51, AttachmentContent.class, "聊天附件(图片，音频，视频，文件等)"),
    CHAT_NAME_CARD(52, NameCardContent.class, "名片"),



    BROADCAST(100, String.class, "广播消息");

    /**
     * 唯一标识code
     */
    private int contentType;
    /**
     * 枚举对应的内容具体类
     */
    private Class<?> contentClass;
    /**
     * 描述
     */
    private String description;

    MessageContentEnum(int contentType, Class<?> contentClass, String description) {
        this.contentType = contentType;
        this.contentClass = contentClass;
        this.description = description;
    }


    public int type() {
        return contentType;
    }

    public void setContentType(int contentType) {
        this.contentType = contentType;
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


    public static MessageContentEnum prototype(int contentType) {
        for (MessageContentEnum messageContentEnum : MessageContentEnum.values()) {
            if (messageContentEnum.contentType == contentType) {
                return messageContentEnum;
            }
        }
        return null;
    }
}
