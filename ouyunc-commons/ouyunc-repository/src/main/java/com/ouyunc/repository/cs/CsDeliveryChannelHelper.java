package com.ouyunc.repository.cs;

import com.ouyunc.base.constant.enums.MessageDeliveryChannelEnum;
import org.apache.commons.lang3.StringUtils;

/**
 * 客服会话下行渠道：由 CS 写入 route 的 {@code channelType}（协议）解析。
 * {@code channel} 只标识实例（whatsapp_a），不参与协议识别。
 */
public final class CsDeliveryChannelHelper {

    private CsDeliveryChannelHelper() {
    }

    /**
     * 解析接收方下行渠道：需长连的坐席始终 IM；访客按 route.channelType 走外渠 Kafka。
     * <p>机器人/虚拟客服不走坐席长连，由调用方在投递前短路。</p>
     */
    public static MessageDeliveryChannelEnum resolveRecipientChannel(CsImSessionRoute route, String recipientId) {
        if (route == null || StringUtils.isBlank(recipientId)) {
            return MessageDeliveryChannelEnum.IM;
        }
        if (StringUtils.equals(recipientId, route.assigneeId())) {
            return MessageDeliveryChannelEnum.IM;
        }
        if (StringUtils.equals(recipientId, route.userId())) {
            return fromChannelType(route.channelType());
        }
        return MessageDeliveryChannelEnum.IM;
    }

    /**
     * Redis route.channelType / ticket.channelType：协议键。im 走长连接；whatsapp/telegram/line 走 Kafka。
     */
    public static MessageDeliveryChannelEnum fromChannelType(String channelType) {
        if (StringUtils.isBlank(channelType)) {
            return MessageDeliveryChannelEnum.IM;
        }
        MessageDeliveryChannelEnum channel = MessageDeliveryChannelEnum.fromKey(channelType.trim());
        if (channel.isExternalMessaging()) {
            return channel;
        }
        return MessageDeliveryChannelEnum.IM;
    }

    public static boolean isExternalVisitorRoute(CsImSessionRoute route) {
        return route != null && fromChannelType(route.channelType()).isExternalMessaging();
    }
}
