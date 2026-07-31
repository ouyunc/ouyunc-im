package com.ouyunc.base.constant.enums;

import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.content.ChatFileContent;
import com.ouyunc.base.packet.message.content.GroupRequestContent;
import com.ouyunc.base.packet.message.content.ImageContent;
import com.ouyunc.base.packet.message.content.ImageTextContent;
import com.ouyunc.base.packet.message.content.LocationContent;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.packet.message.content.LogisticsCardContent;
import com.ouyunc.base.packet.message.content.OrderCardContent;
import com.ouyunc.base.packet.message.content.PostcardContent;
import com.ouyunc.base.packet.message.content.ProductCardContent;
import com.ouyunc.base.packet.message.content.ProfileCardContent;
import com.ouyunc.base.packet.message.content.RecommendItemContent;
import com.ouyunc.base.packet.message.content.VideoCallContent;
import com.ouyunc.base.packet.message.content.VideoContent;
import com.ouyunc.base.packet.message.content.VoiceCallContent;
import com.ouyunc.base.packet.message.content.VoiceContent;

import java.util.List;

/**
 * @author fzx
 * @description 消息内容类型枚举
 */
public enum MessageContentTypeEnum implements MessageContentType {
    PING_PONG_CONTENT(NumberConstant.NUMBER_NEGATIVE_1,ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), String.class, "外部消息心跳ping消息内容"),
    LOGIN_REQUEST_CONTENT(NumberConstant.NUMBER_NEGATIVE_2,ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), LoginContent.class, "外部客户端登录消息内容"),
    LOGIN_RESPONSE_FAIL_CONTENT(NumberConstant.NUMBER_NEGATIVE_3,ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), String.class, "外部客户端登录失败消息内容"),
    LOGIN_RESPONSE_SUCCESS_CONTENT(NumberConstant.NUMBER_NEGATIVE_4,ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), String.class, "客户端登录成功"),
    QOS_DUP_CONTENT(NumberConstant.NUMBER_NEGATIVE_5, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), Packet.class,  "qos  客户端重发消息的消息内容"),

    WITHDRAW_CONTENT(NumberConstant.NUMBER_NEGATIVE_6, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), List.class,  "撤销消息的消息内容"),
    READ_RECEIPT_CONTENT(NumberConstant.NUMBER_NEGATIVE_7, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), List.class,  "读已回执消息内容"),

    GROUP_REQUEST_CONTENT(NumberConstant.NUMBER_NEGATIVE_8, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), GroupRequestContent.class,  "群请求消息内容"),

    /** 同账号同设备（sn 相同）重复登录，通知旧连接下线 */
    DUPLICATE_LOGIN_CONTENT(NumberConstant.NUMBER_NEGATIVE_9, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), String.class, "同设备重复登录通知"),
    /** 同账号异设备（sn 不同）登录，通知旧连接下线 */
    REMOTE_LOGIN_CONTENT(NumberConstant.NUMBER_NEGATIVE_10, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), String.class, "远程登录踢下线通知"),

    /** 纯图片消息 */
    IMAGE_CONTENT(NumberConstant.NUMBER_NEGATIVE_120, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), ImageContent.class, "图片消息内容"),
    /** 附件/文件消息 */
    FILE_CONTENT(NumberConstant.NUMBER_NEGATIVE_121, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), ChatFileContent.class, "文件附件消息内容"),
    /** 语音消息 */
    VOICE_CONTENT(NumberConstant.NUMBER_NEGATIVE_122, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), VoiceContent.class, "语音消息内容"),
    /** 图文混合消息 */
    IMAGE_TEXT_CONTENT(NumberConstant.NUMBER_NEGATIVE_123, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), ImageTextContent.class, "图文混合消息内容"),
    /** 语音通话记录 */
    VOICE_CALL_CONTENT(NumberConstant.NUMBER_NEGATIVE_124, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), VoiceCallContent.class, "语音通话记录内容"),
    /** 视频通话记录 */
    VIDEO_CALL_CONTENT(NumberConstant.NUMBER_NEGATIVE_125, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), VideoCallContent.class, "视频通话记录内容"),
    /** 视频消息 */
    VIDEO_CONTENT(NumberConstant.NUMBER_NEGATIVE_126, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), VideoContent.class, "视频消息内容"),

    /** 地图/位置消息 */
    LOCATION_CONTENT(NumberConstant.NUMBER_NEGATIVE_119, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), LocationContent.class, "地图位置消息内容"),
    /** 明信片/贺卡消息 */
    POSTCARD_CONTENT(NumberConstant.NUMBER_NEGATIVE_118, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), PostcardContent.class, "明信片贺卡消息内容"),
    /** 商品卡片（可带 recommendText 表示推荐商品） */
    PRODUCT_CARD_CONTENT(NumberConstant.NUMBER_NEGATIVE_117, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), ProductCardContent.class, "商品卡片消息内容"),
    /** 订单卡片 */
    ORDER_CARD_CONTENT(NumberConstant.NUMBER_NEGATIVE_116, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), OrderCardContent.class, "订单卡片消息内容"),
    /** 物流卡片 */
    LOGISTICS_CARD_CONTENT(NumberConstant.NUMBER_NEGATIVE_115, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), LogisticsCardContent.class, "物流卡片消息内容"),
    /** 推荐人/名片卡片 */
    PROFILE_CARD_CONTENT(NumberConstant.NUMBER_NEGATIVE_114, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), ProfileCardContent.class, "推荐人名片消息内容"),
    /** 通用推荐物（文章/门店/优惠券等，非商品） */
    RECOMMEND_ITEM_CONTENT(NumberConstant.NUMBER_NEGATIVE_113, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), RecommendItemContent.class, "通用推荐物消息内容"),

    TEXT_CONTENT(NumberConstant.NUMBER_NEGATIVE_128, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), String.class, "通用文本内容类型"),

    ;
    /**
     * 唯一标识code
     */
    private int type;

    private byte protocol;

    private byte protocolVersion;
    /**
     * 枚举对应的内容具体类
     */
    private Class<?> contentClass;
    /**
     * 描述
     */
    private String description;

    MessageContentTypeEnum(int messageContentType, byte protocol, byte protocolVersion, Class<?> contentClass, String description) {
        this.type = messageContentType;
        this.protocol = protocol;
        this.protocolVersion = protocolVersion;
        this.contentClass = contentClass;
        this.description = description;
    }

    @Override
    public byte getProtocol() {
        return protocol;
    }

    public void setProtocol(byte protocol) {
        this.protocol = protocol;
    }

    @Override
    public byte getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(byte protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
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

    public static MessageContentTypeEnum getByType(int contentType) {
        for (MessageContentTypeEnum e : values()) {
            if (e.type == contentType) {
                return e;
            }
        }
        return null;
    }

}
