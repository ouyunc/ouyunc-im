package com.ouyunc.message.helper;

import com.ouyunc.base.constant.enums.MessageDeliveryChannelEnum;
import com.ouyunc.base.model.ClientInfo;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.cs.CsImSessionRoute;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 客服 IM 投递：{@code service_identity} 解析为当前 {@code assigneeId} 的在线 Channel。
 */
public final class CsMessageDeliveryRouter {

    private static final Logger log = LoggerFactory.getLogger(CsMessageDeliveryRouter.class);

    private CsMessageDeliveryRouter() {
    }

    public static void deliverCustomerServiceMessage(Packet packet, CsImSessionRoute route, boolean forceSelfSync) {
        if (route == null) {
            log.error("客服投递缺少会话路由, packetId={}", packet.getPacketId());
            return;
        }
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        syncCsSenderDevices(packet, route, forceSelfSync);

        String recipientId = resolveImRecipientId(message.getTo(), route);
        if (StringUtils.isBlank(recipientId)) {
            log.debug("客服消息暂无 IM 投递目标 to={}, 已落库", message.getTo());
            return;
        }
        MessageDeliveryChannelEnum channel = DefaultRepository.INSTANCE.resolveFriendDeliveryChannel(
                appKey, message.getFrom(), recipientId);
        if (channel.isIm()) {
            pushImUserIfOnline(packet, appKey, recipientId);
        } else {
            DefaultRepository.INSTANCE.publishExternalChannelOutbound(packet, recipientId, channel);
        }
    }

    public static void deliverReadReceiptToOriginalSender(Packet packet, CsImSessionRoute route) {
        if (route == null) {
            log.error("已读回执投递缺少会话路由, packetId={}", packet.getPacketId());
            return;
        }
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        String recipientId = resolveImRecipientId(message.getTo(), route);
        if (StringUtils.isBlank(recipientId)) {
            log.debug("已读回执暂无投递目标 to={}", message.getTo());
            return;
        }
        List<LoginClientInfo> senderClients = ClientHelper.onlineAll(appKey, recipientId);
        if (CollectionUtils.isNotEmpty(senderClients)) {
            MessageHelper.asyncSendMessage(packet, senderClients);
        }
    }

    public static String resolveImRecipientId(String recipientId, CsImSessionRoute route) {
        if (StringUtils.isBlank(recipientId) || route == null) {
            return null;
        }
        if (StringUtils.equals(recipientId, route.serviceIdentity())) {
            return route.assigneeId();
        }
        return recipientId;
    }

    private static void pushImUserIfOnline(Packet packet, String appKey, String userId) {
        List<LoginClientInfo> clients = ClientHelper.onlineAll(appKey, userId);
        if (CollectionUtils.isEmpty(clients)) {
            log.debug("IM 用户 {} 不在线，已写入会话索引", userId);
            return;
        }
        MessageHelper.asyncSendMessage(packet, clients);
    }

    private static void syncCsSenderDevices(Packet packet, CsImSessionRoute route, boolean forceSelfSync) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        String syncIdentity = resolveSenderSyncIdentity(message.getFrom(), route);
        if (StringUtils.isBlank(syncIdentity)) {
            return;
        }
        ClientInfo clientInfo = MessageServerContext.localClientInfo(appKey, syncIdentity);
        if (!forceSelfSync && (clientInfo == null || !clientInfo.getSelfSync())) {
            return;
        }
        List<LoginClientInfo> senderDevices = ClientHelper.onlineAll(
                appKey, syncIdentity, MessageServerContext.deviceType(appKey, packet.getDeviceType()));
        if (CollectionUtils.isNotEmpty(senderDevices)) {
            MessageHelper.asyncSendMessage(packet, senderDevices);
        }
    }

    private static String resolveSenderSyncIdentity(String from, CsImSessionRoute route) {
        if (route == null) {
            return from;
        }
        if (StringUtils.equals(from, route.serviceIdentity())) {
            return route.assigneeId();
        }
        return from;
    }
}
