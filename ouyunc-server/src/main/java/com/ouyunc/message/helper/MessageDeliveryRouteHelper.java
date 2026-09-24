package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
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
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
        if (memberId == null) {
            return;
        }
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        Set<String> deliverable = DefaultRepository.INSTANCE.excludeGroupShieldedMembers(
                appKey, groupId, Set.of(memberId));
        if (deliverable.isEmpty()) {
            return;
        }
        MessageDeliveryChannelEnum channel =
                DefaultRepository.INSTANCE.resolveGroupMemberDeliveryChannel(appKey, groupId, memberId);
        routeToRecipient(packet, memberId, channel, "群聊");
    }

    /**
     * 群消息批量投递：IM 成员批量查在线，外渠成员逐条发 Kafka。已屏蔽本群的成员不投递。
     */
    public static void deliverGroupMembers(Packet packet, Set<String> memberIds) {
        if (CollectionUtils.isEmpty(memberIds)) {
            return;
        }
        int batchSize = MessageConstant.GROUP_FANOUT_ONLINE_LOOKUP_BATCH;
        Set<String> batch = new HashSet<>(batchSize);
        for (String member : memberIds) {
            if (member != null && !member.equals(packet.getMessage().getFrom())) {
                batch.add(member);
            }
            if (batch.size() >= batchSize) {
                deliverGroupBatch(packet, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            deliverGroupBatch(packet, batch);
        }
    }

    /** 每批完成过滤、渠道和在线查询后释放临时集合，避免整群渠道 Map 和 IM Set 叠加。 */
    private static void deliverGroupBatch(Packet packet, Set<String> members) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        Set<String> deliverable = DefaultRepository.INSTANCE.excludeGroupShieldedMembers(
                appKey, message.getTo(), members);
        Map<String, MessageDeliveryChannelEnum> channels = DefaultRepository.INSTANCE
                .resolveGroupMemberDeliveryChannels(appKey, message.getTo(), deliverable);
        Set<String> imMembers = new HashSet<>();
        List<CompletableFuture<?>> confirms = new ArrayList<>(MessageConstant.GROUP_EXTERNAL_CHANNEL_CONFIRM_BATCH);
        for (String member : deliverable) {
            MessageDeliveryChannelEnum channel = channels.getOrDefault(member, MessageDeliveryChannelEnum.IM);
            if (channel.isIm()) {
                imMembers.add(member);
            } else {
                confirms.add(DefaultRepository.INSTANCE.publishExternalChannelOutbound(packet, member, channel));
                if (confirms.size() >= MessageConstant.GROUP_EXTERNAL_CHANNEL_CONFIRM_BATCH) {
                    awaitExternalIfHttp(packet, confirms);
                    confirms.clear();
                }
            }
        }
        awaitExternalIfHttp(packet, confirms);
        if (!imMembers.isEmpty()) {
            sendImGroupMembers(packet, appKey, imMembers);
        }
    }
    private static void sendImGroupMembers(Packet packet, String appKey, Set<String> imMembers) {
        Map<String, List<LoginClientInfo>> onlineMap = ClientHelper.onlineAllBatch(appKey, imMembers);
        // 本批所有设备一次交给 fanout，按节点合并正文；下游继续限制每帧的目标数。
        List<LoginClientInfo> recipients = new java.util.ArrayList<>();
        onlineMap.forEach((member, clients) -> {
            if (CollectionUtils.isNotEmpty(clients)) {
                recipients.addAll(clients);
            } else {
                log.debug("群 IM 成员 {} 不在线，已写入群会话索引", member);
            }
        });
        if (!recipients.isEmpty()) {
            MessageHelper.asyncSendMessage(packet, recipients);
        }
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
        CompletableFuture<?> confirmed = DefaultRepository.INSTANCE.publishExternalChannelOutbound(
                packet, recipientId, channel);
        awaitExternalIfHttp(packet, List.of(confirmed));
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
                packet.getDeviceType());
        if (CollectionUtils.isNotEmpty(senderDevices)) {
            MessageHelper.asyncSendMessage(packet, senderDevices);
        }
    }

    private static boolean isHttpPush(Metadata metadata) {
        return metadata != null && IngressSourceEnum.isHttpPush(metadata.getIngressSource());
    }

    /** HTTP 受理必须知道外部任务是否进 broker；长连接保持原异步行为。 */
    private static void awaitExternalIfHttp(Packet packet, List<CompletableFuture<?>> confirms) {
        if (packet == null || packet.getMessage() == null || confirms == null || confirms.isEmpty()
                || !isHttpPush(packet.getMessage().getMetadata())) {
            return;
        }
        try {
            CompletableFuture.allOf(confirms.toArray(CompletableFuture[]::new))
                    .get(MessageConstant.EXTERNAL_CHANNEL_CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception error) {
            throw new IllegalStateException("HTTP 外部渠道任务 broker 确认失败", error);
        }
    }
}
