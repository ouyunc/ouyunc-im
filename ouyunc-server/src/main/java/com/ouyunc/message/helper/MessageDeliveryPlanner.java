package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.IngressSourceEnum;
import com.ouyunc.base.constant.enums.ClusterForwardModeEnum;
import com.ouyunc.base.constant.enums.MessageDeliveryChannelEnum;
import com.ouyunc.base.model.ClientInfo;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.exception.ExternalDeliveryConfirmException;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.schedule.QosRetryScheduler;
import com.ouyunc.repository.DefaultRepository;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 按好友/群成员 {@code channel} 路由下行：IM 多设备推送或外部渠道 Kafka 出站。
 */
public final class MessageDeliveryPlanner {

    private static final Logger log = LoggerFactory.getLogger(MessageDeliveryPlanner.class);

    private MessageDeliveryPlanner() {
    }

    /**
     * 将已解析出的在线设备提交到统一发送入口。
     *
     * <p>本机设备逐目标调用 {@link MessageSender#send(Packet, Target)}，保持拦截器和 QoS 语义一致；
     * 远端设备先按最终落地节点聚合正文，再构造 CLIENT 续传包调用同一个入口，避免跨节点逐设备复制正文。
     * CLIENT 包在 {@code MessageSender} 内部只继续投递，不会重复执行首次业务拦截器。</p>
     */
    public static void deliverOnlineClients(Packet packet, List<LoginClientInfo> clients) {
        if (packet == null || CollectionUtils.isEmpty(clients)) {
            return;
        }
        String localAddress = MessageServerContext.serverProperties().getLocalServerAddress();
        boolean clusterEnabled = MessageServerContext.serverProperties().isClusterEnable();
        Map<String, List<LoginClientInfo>> remoteByNode = new LinkedHashMap<>();
        for (LoginClientInfo client : clients) {
            if (client == null) {
                continue;
            }
            Target target = toTarget(client, localAddress);
            String targetAddress = target.getTargetServerAddress();
            if (!clusterEnabled || localAddress.equals(targetAddress)) {
                MessageSender.send(packet, target);
                continue;
            }
            remoteByNode.computeIfAbsent(targetAddress, ignored -> new ArrayList<>()).add(client);
        }
        remoteByNode.forEach((nodeAddress, nodeClients) -> deliverRemoteNodeBatches(packet, nodeAddress, nodeClients));
    }

    private static Target toTarget(LoginClientInfo client, String localAddress) {
        String address = client.getLoginServerAddress();
        if (address == null || address.isBlank()) {
            address = localAddress;
        }
        return Target.newBuilder()
                .appKey(client.getAppKey())
                .targetIdentity(client.getIdentity())
                .targetServerAddress(address)
                .deviceType(client.getDeviceType())
                .build();
    }

    private static void deliverRemoteNodeBatches(Packet packet, String nodeAddress,
                                                 List<LoginClientInfo> nodeClients) {
        // 首次远端聚合绕过单目标拦截器，因此在始发节点按真实设备登记 QoS；
        // CLIENT 续传（登录跟随、跨节点落地）已经登记过，不能再次创建定时任务。
        Metadata sourceMetadata = packet.getMessage() == null ? null : packet.getMessage().getMetadata();
        if (sourceMetadata == null || !sourceMetadata.isClientForward()) {
            QosRetryScheduler.scheduleForClients(packet, nodeClients);
        }
        int batchSize = MessageConstant.GROUP_FANOUT_REMOTE_TARGET_BATCH;
        for (int from = 0; from < nodeClients.size(); from += batchSize) {
            int to = Math.min(from + batchSize, nodeClients.size());
            List<Target> targets = new ArrayList<>(to - from);
            for (int index = from; index < to; index++) {
                targets.add(toTarget(nodeClients.get(index), nodeAddress));
            }
            Packet fanoutPacket = packet.clone();
            Metadata metadata = fanoutPacket.getMessage().ensureMetadata();
            metadata.ensureClusterRoute().setClusterForwardMode(ClusterForwardModeEnum.CLIENT);
            metadata.ensureClusterRoute().setFanoutTargets(targets);
            Target envelopeTarget = Target.newBuilder()
                    .appKey(metadata.getIngress().getAppKey())
                    .targetServerAddress(nodeAddress)
                    .build();
            MessageSender.send(fanoutPacket, envelopeTarget);
        }
    }

    /**
     * 单聊：发送方多设备同步 + 按接收方好友 channel 投递。
     */
    public static void deliverPeerMessage(Packet packet, boolean forceSelfSync) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getIngress().getAppKey();
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
        String appKey = message.getMetadata().getIngress().getAppKey();
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
        deliverGroupMembers(packet, memberIds, () -> { });
    }

    /**
     * 群成员分批投递。{@code beforeBatch} 在每一批产生 IM/MQ 副作用前执行，
     * 用于长时间首次扇出续租和及时感知 owner 丢失。
     */
    public static void deliverGroupMembers(Packet packet, Set<String> memberIds, Runnable beforeBatch) {
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
                beforeBatch.run();
                deliverGroupBatch(packet, batch, beforeBatch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            beforeBatch.run();
            deliverGroupBatch(packet, batch, beforeBatch);
        }
    }

    /** 每批完成过滤、渠道和在线查询后释放临时集合，避免整群渠道 Map 和 IM Set 叠加。 */
    private static void deliverGroupBatch(Packet packet, Set<String> members, Runnable beforeExternalBatch) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getIngress().getAppKey();
        Set<String> deliverable = DefaultRepository.INSTANCE.excludeGroupShieldedMembers(
                appKey, message.getTo(), members);
        Map<String, MessageDeliveryChannelEnum> channels = DefaultRepository.INSTANCE
                .resolveGroupMemberDeliveryChannels(appKey, message.getTo(), deliverable);
        Set<String> imMembers = new HashSet<>();
        List<CompletableFuture<?>> confirms = new ArrayList<>(MessageConstant.GROUP_EXTERNAL_CHANNEL_CONFIRM_BATCH);
        List<String> externalMembers = new ArrayList<>(MessageConstant.GROUP_EXTERNAL_CHANNEL_CONFIRM_BATCH);
        List<MessageDeliveryChannelEnum> externalChannels = new ArrayList<>(MessageConstant.GROUP_EXTERNAL_CHANNEL_CONFIRM_BATCH);
        for (String member : deliverable) {
            MessageDeliveryChannelEnum channel = channels.getOrDefault(member, MessageDeliveryChannelEnum.IM);
            if (channel.isIm()) {
                imMembers.add(member);
                continue;
            }
            if (!beginExternalTask(packet, member, channel)) {
                continue;
            }
            // 外渠每 64 人可能等待一次 broker 确认，在新一批发布前再续租，
            // 避免多个慢批次累计超过 delivery 锁 TTL。续租失败时保留 PENDING 供下次重试。
            if (confirms.isEmpty()) {
                beforeExternalBatch.run();
            }
            externalMembers.add(member);
            externalChannels.add(channel);
            confirms.add(DefaultRepository.INSTANCE.publishExternalChannelOutbound(packet, member, channel));
            if (confirms.size() >= MessageConstant.GROUP_EXTERNAL_CHANNEL_CONFIRM_BATCH) {
                awaitExternalConfirm(packet, confirms, externalMembers, externalChannels);
                confirms.clear();
                externalMembers.clear();
                externalChannels.clear();
            }
        }
        awaitExternalConfirm(packet, confirms, externalMembers, externalChannels);
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
            deliverOnlineClients(packet, recipients);
        }
    }

    private static void pushImUserIfOnline(Packet packet, String userId) {
        Message message = packet.getMessage();
        List<LoginClientInfo> clients = ClientHelper.onlineAll(message.getMetadata().getIngress().getAppKey(), userId);
        if (CollectionUtils.isEmpty(clients)) {
            log.debug("IM 用户 {} 不在线，已写入会话索引", userId);
            return;
        }
        deliverOnlineClients(packet, clients);
    }

    private static void routeToRecipient(Packet packet, String recipientId,
                                         MessageDeliveryChannelEnum channel, String logLabel) {
        if (channel.isIm()) {
            pushImUserIfOnline(packet, recipientId);
            return;
        }
        if (!beginExternalTask(packet, recipientId, channel)) {
            return;
        }
        CompletableFuture<?> confirmed = DefaultRepository.INSTANCE.publishExternalChannelOutbound(
                packet, recipientId, channel);
        awaitExternalConfirm(packet, List.of(confirmed), List.of(recipientId), List.of(channel));
    }

    private static void syncSenderDevices(Packet packet, boolean forceSelfSync) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getIngress().getAppKey();
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
            deliverOnlineClients(packet, senderDevices);
        }
    }

    private static boolean isHttpPush(Metadata metadata) {
        return metadata != null && IngressSourceEnum.isHttpPush(metadata.getIngress().getIngressSource());
    }

    /**
     * 发布前先落下收件人任务。已经确认过的收件人不再发布。
     *
     * @return false 表示该收件人已经确认
     */
    private static boolean beginExternalTask(Packet packet, String recipientId, MessageDeliveryChannelEnum channel) {
        if (DefaultRepository.INSTANCE.isExternalRecipientConfirmed(packet, recipientId, channel)) {
            return false;
        }
        if (!DefaultRepository.INSTANCE.markExternalRecipientPending(packet, recipientId, channel)) {
            if (DefaultRepository.INSTANCE.isExternalRecipientConfirmed(packet, recipientId, channel)) {
                return false;
            }
            throw new ExternalDeliveryConfirmException("外渠任务身份不足，无法记录恢复标记", null);
        }
        return true;
    }

    /**
     * 只确认 broker 已成功的收件人。同批其他人失败时不得把已成功项留在 PENDING，
     * 否则下一次重入会把已经发出的外渠再发一遍。
     */
    private static void confirmSucceeded(Packet packet, List<CompletableFuture<?>> confirms,
                                         List<String> recipients, List<MessageDeliveryChannelEnum> channels) {
        int size = Math.min(confirms.size(), Math.min(recipients.size(), channels.size()));
        for (int index = 0; index < size; index++) {
            CompletableFuture<?> confirm = confirms.get(index);
            if (confirm.isDone() && !confirm.isCompletedExceptionally() && !confirm.isCancelled()) {
                DefaultRepository.INSTANCE.confirmExternalRecipient(packet, recipients.get(index), channels.get(index));
            }
        }
    }

    /**
     * 长连接和 HTTP 都要等外渠进入 broker，失败时不得回受理成功。任务标记在等待之前已经写入。
     * 超时或个别失败时，先记下本批已经成功的收件人，再把异常抛给受理链。
     */
    private static void awaitExternalConfirm(Packet packet, List<CompletableFuture<?>> confirms,
                                             List<String> recipients, List<MessageDeliveryChannelEnum> channels) {
        if (packet == null || confirms == null || confirms.isEmpty()) {
            return;
        }
        try {
            CompletableFuture.allOf(confirms.toArray(CompletableFuture[]::new))
                    .get(MessageConstant.EXTERNAL_CHANNEL_CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            confirmSucceeded(packet, confirms, recipients, channels);
        } catch (Exception error) {
            confirmSucceeded(packet, confirms, recipients, channels);
            throw new ExternalDeliveryConfirmException("外部渠道任务 broker 确认失败", error);
        }
    }
}
