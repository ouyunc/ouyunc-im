package com.ouyunc.base.constant;

/**
 * 跨渠道消息写入 {@link com.ouyunc.base.packet.message.Message#extra} 的键名。
 */
public final class ChannelMessageExtraKeys {

    /** 好友/群成员投递渠道 code，见 {@link com.ouyunc.base.constant.enums.MessageDeliveryChannelEnum} */
    public static final String DELIVERY_CHANNEL = "deliveryChannel";
    /** 入站来源渠道 key（whatsapp / telegram），HTTP Push 时映射为 metadata.ingressSource */
    public static final String INGRESS_CHANNEL = "ingressChannel";
    public static final String INGRESS_SOURCE = "ingressSource";

    private ChannelMessageExtraKeys() {
    }
}
