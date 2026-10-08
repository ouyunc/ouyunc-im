package com.ouyunc.message.schedule;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ouyunc.base.constant.MessageConstant;
import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.QosControlConstant;
import com.ouyunc.base.constant.enums.ClusterForwardModeEnum;
import com.ouyunc.base.constant.enums.DeviceTypeEnum;
import com.ouyunc.base.constant.enums.NetworkEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageTypeEnum;
import com.ouyunc.base.constant.enums.QosLevelEnum;
import com.ouyunc.base.constant.enums.QosModeEnum;
import com.ouyunc.base.constant.enums.SendStatusEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.SendCallback;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.QosRetryCancelContent;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.cluster.lease.NodeLeaseKeeper;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageSender;
import com.ouyunc.message.monitor.QosRetryCancelMetrics;
import com.ouyunc.message.protocol.NativePacketProtocol;
import com.ouyunc.repository.DefaultRepository;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * SERVER QoS 下行重试：任务只挂在始发节点。落地节点只写出；C2S ACK 若打到落地机，再转回始发节点取消。
 * <p>内部取消是 QoS0，发送失败仅有界重试；丢失后依赖客户端再次 ACK。取消成功仍可能有一条已在途的重复下行，
 * 客户端须按消息 ID 去重。始发进程宕机则内存 timer 丢失。重连后由客户端主动拉取会话历史补齐，服务端不持久化或跨节点接管重试任务。</p>
 */
public final class QosRetryScheduler {

    /**
     * 最终落地节点已实际处理过的 QoS 下行证明。ACK 通常沿同一长连接回到本节点，
     * 命中时无需再从 Redis 反序列化完整 Packet；未命中仍保留 Redis 权威回退。
     */
    private static final Cache<String, AckProof> ACK_PROOFS = Caffeine.newBuilder()
            .maximumSize(MessageConstant.QOS_ACK_PROOF_LOCAL_CACHE_MAX_SIZE)
            .expireAfterWrite(MessageConstant.QOS_ACK_PROOF_LOCAL_CACHE_TTL_SECONDS, TimeUnit.SECONDS)
            .build();

    private static final Logger log = LoggerFactory.getLogger(QosRetryScheduler.class);

    private QosRetryScheduler() {
    }

    public static boolean retryEnabled() {
        return MessageContext.isQosEnable()
                && MessageServerContext.serverProperties().isQosRetryEnable()
                // 0 表示关闭重试，连空定时任务也不登记，避免扇出时占用时间轮和缓存。
                && MessageServerContext.serverProperties().getQosRetryMaxLoops() > 0
                && QosModeEnum.SERVER.equals(MessageServerContext.serverProperties().getQosMode());
    }

    public static void scheduleForClients(Packet packet, Collection<LoginClientInfo> clients) {
        if (!retryEnabled() || packet == null || CollectionUtils.isEmpty(clients)) {
            return;
        }
        if (!isOriginNode(packet)) {
            return;
        }
        for (LoginClientInfo client : clients) {
            if (client != null) {
                schedule(packet, Target.newBuilder()
                        .appKey(client.getAppKey())
                        .targetIdentity(client.getIdentity())
                        .targetServerAddress(client.getLoginServerAddress())
                        .deviceType(client.getDeviceType())
                        .build());
            }
        }
    }

    /**
     * 始发节点按接收端（identity+deviceType）登记。落地写出、重推路径不要再调。
     */
    public static void schedule(Packet packet, Target target) {
        if (!retryEnabled() || packet == null || packet.getMessage() == null || target == null) {
            return;
        }
        Message message = packet.getMessage();
        if (message.getMetadata() == null || StringUtils.isBlank(target.getTargetIdentity())) {
            return;
        }
        if (message.getQos() <= QosLevelEnum.QOS_0.getLevel()) {
            return;
        }
        if (StringUtils.isBlank(message.getMetadata().getIngress().getOriginServerAddress())) {
            // 来源只能由外部入站层建立；落地节点补值会把自己误判为始发节点。
            log.warn("QoS 重试拒绝登记：缺少入站 origin packetId={}", packet.getPacketId());
            return;
        }
        if (!isOriginNode(packet)) {
            return;
        }
        String appKey = message.getMetadata().getIngress().getAppKey();
        QosRetryTaskContext retryContext = new QosRetryTaskContext(
                appKey, packet.getPacketId(), target.getAppKey(), target.getTargetIdentity(), target.getDeviceType());
        String taskId = QosRetryTaskIds.build(retryContext);
        if (StringUtils.isBlank(taskId)) {
            return;
        }
        ScheduleTimer.scheduleWithFixedDelay(taskId, taskWrapper -> retryOnce(retryContext, taskId, taskWrapper),
                MessageServerContext.serverProperties().getQosRetryInitialDelay(),
                MessageServerContext.serverProperties().getQosRetryPeriod(),
                TimeUnit.SECONDS,
                MessageServerContext.serverProperties().getQosRetryMaxLoops());
    }

    /**
     * C2S ACK：先按认证租户查询原消息并校验，再取消本机任务或转给原消息始发节点。
     * 不给 to 回 S2C：C2S 丢失时由始发节点继续下行重试即可自愈。
     */
    public static void onClientAck(LoginClientInfo login, long packetId, String messageId) {
        if (!retryEnabled() || login == null || packetId <= 0 || StringUtils.isBlank(messageId)) {
            return;
        }
        if (StringUtils.isAnyBlank(login.getAppKey(), login.getIdentity())) {
            log.warn("QoS ACK 缺少已认证登录身份，忽略取消重试");
            return;
        }
        String proofKey = ackProofKey(login.getAppKey(), packetId);
        AckProof proof = ACK_PROOFS.getIfPresent(proofKey);
        Packet stored = null;
        QosAckValidation.MatchResult match;
        String origin;
        String storedMessageId;
        if (proof != null) {
            match = proof.matches(login.getAppKey(), packetId, messageId)
                    ? QosAckValidation.MatchResult.OK
                    : QosAckValidation.MatchResult.MESSAGE_ID_MISMATCH;
            origin = proof.originServerAddress();
            storedMessageId = proof.messageId();
        } else {
            try {
                stored = loadStoredPacket(login.getAppKey(), packetId);
            } catch (Exception e) {
                QosRetryCancelMetrics.originLocateFail();
                log.warn("QoS ACK 查询原消息失败 appKey={} packetId={} messageId={}",
                        login.getAppKey(), packetId, messageId, e);
                return;
            }
            match = QosAckValidation.match(stored, login.getAppKey(), packetId, messageId);
            // 只有完整校验通过时 ingress 才被 QosAckValidation 证明非空，避免旧格式/脏 Packet 触发空指针。
            origin = match == QosAckValidation.MatchResult.OK
                    ? stored.getMessage().getMetadata().getIngress().getOriginServerAddress() : null;
            storedMessageId = stored != null && stored.getMessage() != null
                    ? stored.getMessage().getId() : null;
        }
        if (match != QosAckValidation.MatchResult.OK) {
            QosRetryCancelMetrics.invalidPacket();
            log.warn("QoS C2S ACK 校验失败 reason={} appKey={} packetId={} ackMessageId={} storedMessageId={}",
                    match, login.getAppKey(), packetId, messageId, storedMessageId);
            return;
        }
        // 最终收件权限由始发节点实际登记的身份/设备任务约束。
        // 不查当前群成员：已投递后退群的合法 ACK 仍应允许取消。
        String taskId = QosRetryTaskIds.build(login.getAppKey(), packetId, login.getIdentity(), login.getDeviceType());
        if (StringUtils.isBlank(taskId)) {
            return;
        }
        if (ScheduleTimer.cancelIfPresent(taskId)) {
            ACK_PROOFS.invalidate(proofKey);
            QosRetryCancelMetrics.localCancel();
            return;
        }
        String local = MessageServerContext.serverProperties().getLocalServerAddress();
        if (StringUtils.isBlank(origin)) {
            QosRetryCancelMetrics.originLocateFail();
            return;
        }
        if (origin.equals(local)) {
            // 本机已不存在对应任务，证明不再有复用价值，避免占用本地缓存直到自然过期。
            ACK_PROOFS.invalidate(proofKey);
            return;
        }
        if (!MessageServerContext.serverProperties().isClusterEnable()
                || !NodeLeaseKeeper.hasLiveLease(origin)) {
            log.warn("QoS C2S ACK 无法转回始发节点 origin={} packetId={}", origin, packetId);
            QosRetryCancelMetrics.originLocateFail();
            return;
        }
        forwardCancel(origin, new QosRetryCancelContent(
                login.getAppKey(), packetId, login.getIdentity(), login.getDeviceType()), 1);
        ACK_PROOFS.invalidate(proofKey);
    }

    /** 在最终落地 Channel 写成功后登记紧凑 ACK 证明；只缓存校验字段，不持有完整 Packet。 */
    public static void rememberOutbound(Packet packet) {
        if (!retryEnabled() || packet == null || packet.getPacketId() <= 0L || packet.getMessage() == null
                || packet.getMessage().getQos() <= 0 || packet.getMessage().getMetadata() == null
                || packet.getMessage().getMetadata().getIngress() == null) {
            return;
        }
        String appKey = packet.getMessage().getMetadata().getIngress().getAppKey();
        String messageId = packet.getMessage().getId();
        String origin = packet.getMessage().getMetadata().getIngress().getOriginServerAddress();
        if (StringUtils.isAnyBlank(appKey, messageId, origin)) {
            return;
        }
        ACK_PROOFS.put(ackProofKey(appKey, packet.getPacketId()),
                new AckProof(appKey, packet.getPacketId(), messageId, origin));
    }

    private static String ackProofKey(String appKey, long packetId) {
        return appKey + ':' + packetId;
    }

    private static final class AckProof {

        private final String appKey;
        private final long packetId;
        private final String messageId;
        private final String originServerAddress;

        private AckProof(String appKey, long packetId, String messageId, String originServerAddress) {
            this.appKey = appKey;
            this.packetId = packetId;
            this.messageId = messageId;
            this.originServerAddress = originServerAddress;
        }

        private String messageId() {
            return messageId;
        }

        private String originServerAddress() {
            return originServerAddress;
        }

        private boolean matches(String expectedAppKey, long expectedPacketId, String expectedMessageId) {
            return packetId == expectedPacketId && appKey.equals(expectedAppKey) && messageId.equals(expectedMessageId);
        }
    }

    public static boolean onClusterCancel(QosRetryCancelContent content) {
        if (content == null || content.getPacketId() <= 0
                || StringUtils.isAnyBlank(content.getAppKey(), content.getIdentity())) {
            QosRetryCancelMetrics.invalidPacket();
            return false;
        }
        String taskId = QosRetryTaskIds.build(
                content.getAppKey(), content.getPacketId(), content.getIdentity(), content.getDeviceType());
        if (StringUtils.isBlank(taskId)) {
            QosRetryCancelMetrics.invalidPacket();
            return false;
        }
        if (ScheduleTimer.cancelIfPresent(taskId)) {
            QosRetryCancelMetrics.clusterCancelHit();
            return true;
        }
        QosRetryCancelMetrics.clusterCancelMiss();
        return false;
    }

    private static boolean isOriginNode(Packet packet) {
        String local = MessageServerContext.serverProperties().getLocalServerAddress();
        if (StringUtils.isBlank(local) || packet.getMessage() == null
                || packet.getMessage().getMetadata() == null) {
            return false;
        }
        Metadata metadata = packet.getMessage().getMetadata();
        if (StringUtils.isBlank(metadata.getIngress().getOriginServerAddress())) {
            log.warn("QoS 重试无法确定归属：缺少入站 origin packetId={}", packet.getPacketId());
            return false;
        }
        return local.equals(metadata.getIngress().getOriginServerAddress());
    }

    private static void retryOnce(QosRetryTaskContext retryContext, String taskId, TimerTaskWrapper taskWrapper) {
        if (!taskStillActive(taskId, taskWrapper)) {
            return;
        }
        LoginClientInfo device = ClientHelper.onlineDevice(
                retryContext.targetAppKey(), retryContext.targetIdentity(), retryContext.deviceType());
        if (device == null) {
            // 离线只跳过本轮，任务保留到 C2S 或达到最大次数
            return;
        }
        if (!taskStillActive(taskId, taskWrapper)) {
            return;
        }
        Packet schedulePackage = loadRetryPacket(retryContext);
        if (schedulePackage == null) {
            // 热正文暂时不可读时跳过本轮。取消任务会把后续重试也丢掉，恢复只能再靠历史补拉。
            log.warn("QoS 重试加载消息失败，跳过本轮: taskId={}", taskId);
            return;
        }
        if (!taskStillActive(taskId, taskWrapper)) {
            return;
        }
        Packet retryPacket = schedulePackage.clone();
        retryPacket.getMessage().ensureMetadata().ensureClusterRoute()
                .setClusterForwardMode(ClusterForwardModeEnum.CLIENT);
        retryPacket.getMessage().ensureMetadata().ensureClusterRoute().setFanoutTargets(null);
        MessageSender.send(retryPacket, Target.newBuilder()
                .appKey(device.getAppKey())
                .targetIdentity(device.getIdentity())
                .targetServerAddress(device.getLoginServerAddress())
                .deviceType(device.getDeviceType())
                .build());
    }

    private static Packet loadRetryPacket(QosRetryTaskContext retryContext) {
        return loadStoredPacket(retryContext.appKey(), retryContext.packetId());
    }

    private static Packet loadStoredPacket(String appKey, long packetId) {
        List<Packet> packets = DefaultRepository.INSTANCE.getPackets(appKey, Collections.singletonList(packetId));
        if (CollectionUtils.isEmpty(packets) || packets.getFirst() == null) {
            return null;
        }
        return packets.getFirst();
    }

    private static boolean taskStillActive(String taskId, TimerTaskWrapper taskWrapper) {
        return taskWrapper != null && TimerTaskWrapper.lookup(taskId) == taskWrapper;
    }

    private static void forwardCancel(String origin, QosRetryCancelContent content, int attempt) {
        String local = MessageServerContext.serverProperties().getLocalServerAddress();
        long now = TimeUtil.currentTimeMillis();
        Metadata metadata = new Metadata();
        metadata.ensureIngress().setAppKey(content.getAppKey());
        metadata.ensureIngress().setServerTime(now);
        metadata.ensureClusterRoute().setFromServerAddress(local);
        metadata.ensureClusterRoute().setClusterForwardMode(ClusterForwardModeEnum.INTERNAL);
        metadata.ensureClusterRoute().setTarget(Target.newBuilder()
                .appKey(content.getAppKey())
                .targetServerAddress(origin)
                .build());
        Message message = new Message(
                MessageContext.idGenerator().generateIdStr(),
                local,
                origin,
                OuyuncMessageContentTypeEnum.QOS_RETRY_CANCEL_CONTENT.getType(),
                JSON.toJSONString(content),
                QosLevelEnum.QOS_0.getLevel(),
                now,
                metadata);
        Packet packet = new Packet(
                NativePacketProtocol.OUYUNC.getProtocol(),
                NativePacketProtocol.OUYUNC.getProtocolVersion(),
                MessageContext.idGenerator().generateId(),
                DeviceTypeEnum.PC.getType(),
                NetworkEnum.OTHER.getValue(),
                Encrypt.SymmetryEncrypt.NONE.getValue(),
                Serializer.PROTO_STUFF.getValue(),
                OuyuncMessageTypeEnum.QOS_RETRY_CANCEL.getType(),
                message);
        SendCallback onResult = sendResult -> {
            if (sendResult == null || sendResult.getSendStatus() != SendStatusEnum.SEND_FAIL) {
                return;
            }
            if (attempt < QosControlConstant.CANCEL_FORWARD_MAX_ATTEMPTS) {
                QosRetryCancelMetrics.forwardRetry();
                ScheduleTimer.scheduleOnce(
                        () -> forwardCancel(origin, content, attempt + 1), QosControlConstant.CANCEL_FORWARD_DELAY_SECONDS, TimeUnit.SECONDS);
                return;
            }
            QosRetryCancelMetrics.forwardFail();
            Throwable cause = sendResult.getException();
            log.warn("集群 QOS_RETRY_CANCEL 转发失败 origin={} packetId={} cause={}",
                    origin, content.getPacketId(), cause == null ? null : cause.getMessage());
        };
        MessageSender.sendClusterInternal(packet, origin).whenComplete((sendResult, error) -> {
            if (error != null) {
                onResult.onCallback(com.ouyunc.base.model.SendResult.builder()
                        .sendStatus(SendStatusEnum.SEND_FAIL)
                        .packet(packet)
                        .exception(error)
                        .build());
                return;
            }
            onResult.onCallback(sendResult);
        });
    }
}
