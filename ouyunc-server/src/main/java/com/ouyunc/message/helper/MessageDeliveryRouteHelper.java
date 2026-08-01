package com.ouyunc.message.helper;

import com.ouyunc.base.constant.enums.IngressSourceEnum;
import com.ouyunc.base.constant.enums.MessageDeliveryChannelEnum;
import com.ouyunc.base.model.ClientInfo;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.repository.DefaultRepository;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按好友/群成员 {@code channel} 路由下行：IM 多设备推送或外部渠道 Kafka 出站。
 */
public final class MessageDeliveryRouteHelper {

    private static final Logger log = LoggerFactory.getLogger(MessageDeliveryRouteHelper.class);

    private MessageDeliveryRouteHelper() {
    }

    /**
     * 单聊：发送方多设备同步 + 按接收方好友 channel 投递。
     */
    public static void deliverPeerMessage(Packet packet, boolean forceSelfSync) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        syncSenderDevices(packet, forceSelfSync);

        String senderId = message.getFrom();
        String recipientId = message.getTo();
        MessageDeliveryChannelEnum channel =
                DefaultRepository.INSTANCE.resolveFriendDeliveryChannel(appKey, senderId, recipientId);
        routeToRecipient(packet, recipientId, channel, "单聊");
    }

    /**
     * 群消息：向成员投递（调用方已排除发送方或自行过滤）。
     */
    public static void deliverGroupMember(Packet packet, String groupId, String memberId) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        MessageDeliveryChannelEnum channel =
                DefaultRepository.INSTANCE.resolveGroupMemberDeliveryChannel(appKey, groupId, memberId);
        routeToRecipient(packet, memberId, channel, "群聊");
    }

    /**
     * 群消息批量投递：IM 成员批量查在线，外渠成员逐条发 Kafka。
     */
    public static void deliverGroupMembers(Packet packet, Set<String> memberIds) {
        if (CollectionUtils.isEmpty(memberIds)) {
            return;
        }
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        String groupId = message.getTo();
        String senderId = message.getFrom();

        Set<String> imMembers = new HashSet<>();
        for (String memberId : memberIds) {
            if (memberId == null || memberId.equals(senderId)) {
                continue;
            }
            MessageDeliveryChannelEnum channel =
                    DefaultRepository.INSTANCE.resolveGroupMemberDeliveryChannel(appKey, groupId, memberId);
            if (channel.isIm()) {
                imMembers.add(memberId);
            } else {
                DefaultRepository.INSTANCE.publishExternalChannelOutbound(packet, memberId, channel);
            }
        }
        if (imMembers.isEmpty()) {
            return;
        }
        Map<String, List<LoginClientInfo>> onlineMap = ClientHelper.onlineAllBatch(appKey, imMembers);
        onlineMap.forEach((member, clients) -> {
            if (CollectionUtils.isNotEmpty(clients)) {
                MessageHelper.asyncSendMessage(packet, clients);
            } else {
                log.debug("群 IM 成员 {} 不在线，已写入群会话索引", member);
            }
        });
    }

    private static void pushImUserIfOnline(Packet packet, String userId) {
        Message message = packet.getMessage();
        List<LoginClientInfo> clients = ClientHelper.onlineAll(message.getMetadata().getAppKey(), userId);
        if (CollectionUtils.isEmpty(clients)) {
            log.debug("IM 用户 {} 不在线，已写入会话索引", userId);
            return;
        }
        MessageHelper.asyncSendMessage(packet, clients);
    }

    private static void routeToRecipient(Packet packet, String recipientId,
                                         MessageDeliveryChannelEnum channel, String logLabel) {
        if (channel.isIm()) {
            pushImUserIfOnline(packet, recipientId);
            return;
        }
        DefaultRepository.INSTANCE.publishExternalChannelOutbound(packet, recipientId, channel);
    }

    private static void syncSenderDevices(Packet packet, boolean forceSelfSync) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        boolean httpPush = isHttpPush(message.getMetadata());
        if (!forceSelfSync) {
            ClientInfo clientInfo = MessageServerContext.localClientInfo(appKey, message.getFrom());
            if (httpPush) {
                // HTTP：无本地登录默认多端同步；有登录配置则尊重 selfSync
                if (clientInfo != null && !Boolean.TRUE.equals(clientInfo.getSelfSync())) {
                    return;
                }
            } else if (clientInfo == null || !clientInfo.getSelfSync()) {
                return;
            }
        }
        // HTTP 无真实 deviceType，同步时不排除设备
        List<LoginClientInfo> senderDevices = httpPush
                ? ClientHelper.onlineAll(appKey, message.getFrom())
                : ClientHelper.onlineAll(appKey, message.getFrom(),
                MessageServerContext.deviceType(appKey, packet.getDeviceType()));
        if (CollectionUtils.isNotEmpty(senderDevices)) {
            MessageHelper.asyncSendMessage(packet, senderDevices);
        }
    }

    private static boolean isHttpPush(Metadata metadata) {
        return metadata != null && IngressSourceEnum.isHttpPush(metadata.getIngressSource());
    }
}
