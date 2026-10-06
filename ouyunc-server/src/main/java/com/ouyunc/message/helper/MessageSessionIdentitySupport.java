package com.ouyunc.message.helper;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.repository.cs.CsImSessionRoute;
import org.apache.commons.lang3.StringUtils;

/**
 * 正式消息的会话归属。只在业务鉴权通过之后调用，覆盖客户端可能携带的 sessionId。
 * 不按客服坐席与访客的 from/to 推算客服会话，避免转接后拆成多个会话。
 */
public final class MessageSessionIdentitySupport {
    private MessageSessionIdentitySupport() { }

    public static void bindOne2One(Packet packet) {
        Message message = requireMessage(packet);
        message.setSessionId(IdentityUtil.sessionId(message.getFrom(), message.getTo()));
    }

    public static void bindGroup(Packet packet) {
        Message message = requireMessage(packet);
        if (StringUtils.isBlank(message.getTo())) {
            throw new IllegalArgumentException("群消息缺少群 ID");
        }
        message.setSessionId(message.getTo());
    }

    public static void bindCustomerService(Packet packet, CsImSessionRoute route) {
        Message message = requireMessage(packet);
        if (route == null || StringUtils.isAnyBlank(route.ticketId(), route.sessionId(),
                route.userId(), route.serviceIdentity())
                || !StringUtils.equals(message.getCorrelationId(), route.ticketId())
                || !StringUtils.equals(route.sessionId(),
                IdentityUtil.sessionId(route.userId(), route.serviceIdentity()))) {
            throw new IllegalArgumentException("客服消息与咨询单固定会话不一致");
        }
        message.setSessionId(route.sessionId());
    }

    private static Message requireMessage(Packet packet) {
        if (packet == null || packet.getMessage() == null) {
            throw new IllegalArgumentException("缺少消息正文，无法固定会话");
        }
        return packet.getMessage();
    }
}
