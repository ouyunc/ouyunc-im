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

    CLIENT_QOS_NOTIFY_CONTENT(6, ClientQosNotifyContent.class, "外部客户端收到消息后的notify回信内容"),
    SERVER_QOS_ACK_CONTENT(7, String.class, "服务端QOS收到消息后给客户端的ack回信内容(消息id)"),

    SERVER_WARNING_CONTENT(8, ServerWarningContent.class, "服务端发出的警告内容"),
    READ_RECEIPT_CONTENT(9, ReadReceiptContent.class, "已读回执消息内容, 集合中的对象"),
    OFFLINE_CONTENT(10, OfflineContent.class, "离线消息内容"),
    UNREAD_CONTENT(11, UnreadContent.class, "未读离线消息内容"),
    SERVER_NOTIFY_CONTENT(12, ServerNotifyContent.class, "服务端发给客户端的通知内容"),
    QOS_RETRY_PACKET_CONTENT(13, Packet.class, "客户端发起的重试消息packet,注意该packet是上次为发送成功的"),



    FRIEND_JOIN(21, String.class, "添加好友请求"),
    FRIEND_REFUSE(22, String.class, "拒绝好友请求"),
    FRIEND_AGREE(23, String.class, "同意好友请求"),



    GROUP_JOIN(31, GroupRequestContent.class, "加群请求"),
    GROUP_REFUSE(32, GroupRequestContent.class, "管理员/群主 拒绝加群申请"),
    GROUP_AGREE(33, GroupRequestContent.class, "管理员/群主 同意加群申请"),
    GROUP_DISBAND(34, GroupRequestContent.class, "群主 解散群"),
    GROUP_KICK(35, GroupRequestContent.class, "踢出群"),
    GROUP_EXIT(36, GroupRequestContent.class, "退出群"),
    GROUP_SHIELD(37, GroupRequestContent.class, "屏蔽群"),
    GROUP_PUBLISH_ANNOUNCEMENT(38, String.class, "发布群公告"),
    GROUP_INVITE_JOIN(39, GroupRequestContent.class, "群成员邀请他人加群"),
    GROUP_INVITE_AGREE(40, GroupRequestContent.class, "被邀请人同意群成员邀请"),
    GROUP_INVITE_REFUSE(41, GroupRequestContent.class, "被邀请人拒绝群成员邀请"),


    CHAT_TEXT_CONTENT(51, String.class, "聊天文本内容类型"),
    CHAT_ATTACHMENT_CONTENT(52, AttachmentContent.class, "聊天附件(word,pdf,excel文件等)"),
    CHAT_NAME_CARD(53, NameCardContent.class, "聊天名片"),
    CHAT_VOICE_CONTENT(54, VoiceContent.class, "聊天语音"),
    CHAT_PICTURE_CONTENT(55, PictureContent.class, "聊天图片"),
    CHAT_AUDIO_CONTENT(56, AudioContent.class, "聊天音频"),
    CHAT_VIDEO_CONTENT(57, VideoContent.class, "聊天视频"),
    CHAT_VOICE_CALL_RECORD_CONTENT(58, String.class, "语音通话记录"),
    CHAT_VIDEO_CALL_RECORD_CONTENT(59, String.class, "视频通话记录"),



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
