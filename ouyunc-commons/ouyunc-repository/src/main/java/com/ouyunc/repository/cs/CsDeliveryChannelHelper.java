package com.ouyunc.repository.cs;

import com.ouyunc.base.constant.enums.MessageDeliveryChannelEnum;
import org.apache.commons.lang3.StringUtils;

/**
 * 客服会话下行渠道：由 CS 写入 route 的 {@code channel}（ticket.channel）解析，不查好友表。
 */
public final class CsDeliveryChannelHelper {

    private CsDeliveryChannelHelper() {
    }

    /**
     * 解析接收方下行渠道：坐席始终 IM；访客按 ticket 进线渠道（whatsapp/telegram/line → 外渠 Kafka）。
     */
    public static MessageDeliveryChannelEnum resolveRecipientChannel(CsImSessionRoute route, String recipientId) {
        if (route == null || StringUtils.isBlank(recipientId)) {
            return MessageDeliveryChannelEnum.IM;
        }
        if (StringUtils.equals(recipientId, route.assigneeId())) {
            return MessageDeliveryChannelEnum.IM;
        }
        if (StringUtils.equals(recipientId, route.userId())) {
            return fromTicketChannel(route.channel());
        }
        return MessageDeliveryChannelEnum.IM;
    }

    /**
     * CS ticket.channel → IM 投递枚举。im/web/app/h5/pc 等自有终端走长连接；外渠走 Kafka 出站。
     */
    public static MessageDeliveryChannelEnum fromTicketChannel(String ticketChannel) {
        if (StringUtils.isBlank(ticketChannel)) {
            return MessageDeliveryChannelEnum.IM;
        }
        MessageDeliveryChannelEnum channel = MessageDeliveryChannelEnum.fromKey(ticketChannel.trim());
        if (channel.isExternalMessaging()) {
            return channel;
        }
        return MessageDeliveryChannelEnum.IM;
    }

    public static boolean isExternalVisitorRoute(CsImSessionRoute route) {
        return route != null && fromTicketChannel(route.channel()).isExternalMessaging();
    }
}
