package com.ouyunc.message.schedule;

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
import com.ouyunc.message.helper.MessageHelper;
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
 * 客户端须按消息 ID 去重。始发进程宕机则内存 timer 丢失。把重试外置 Redis 会引入双发/抢占，当前不改投递语义。</p>
 */
public final class QosRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(QosRetryScheduler.class);

    private QosRetryScheduler() {
    }

    public static boolean retryEnabled() {
        return MessageContext.isQosEnable()
                && MessageServerContext.serverProperties().isQosRetryEnable()
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
                schedule(packet, MessageHelper.buildTarget(client));
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
        Packet stored;
        try {
            stored = loadStoredPacket(login.getAppKey(), packetId);
        } catch (Exception e) {
            QosRetryCancelMetrics.originLocateFail();
            log.warn("QoS ACK 查询原消息失败 appKey={} packetId={} messageId={}",
                    login.getAppKey(), packetId, messageId, e);
            return;
        }
        QosAckValidation.MatchResult match =
                QosAckValidation.match(stored, login.getAppKey(), packetId, messageId);
        if (match != QosAckValidation.MatchResult.OK) {
            QosRetryCancelMetrics.invalidPacket();
            String storedMessageId = stored != null && stored.getMessage() != null
                    ? stored.getMessage().getId() : null;
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
            QosRetryCancelMetrics.localCancel();
            return;
        }
        String origin = stored.getMessage().getMetadataOrNull().getIngress().getOriginServerAddress();
        String local = MessageServerContext.serverProperties().getLocalServerAddress();
        if (StringUtils.isBlank(origin)) {
            QosRetryCancelMetrics.originLocateFail();
            return;
        }
        if (origin.equals(local)) {
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
            log.warn("QoS 重试加载消息失败，取消任务: taskId={}", taskId);
            taskWrapper.cancel();
            return;
        }
        if (!taskStillActive(taskId, taskWrapper)) {
            return;
        }
        MessageHelper.asyncSendMessageWithoutInterceptor(
                schedulePackage.clone(), MessageHelper.buildTarget(device));
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
        metadata.getIngress().setAppKey(content.getAppKey());
        metadata.getIngress().setServerTime(now);
        metadata.getClusterRoute().setFromServerAddress(local);
        metadata.getClusterRoute().setClusterForwardMode(ClusterForwardModeEnum.INTERNAL);
        metadata.getClusterRoute().setTarget(Target.newBuilder()
                .appKey(content.getAppKey())
                .targetServerAddress(origin)
                .protocol(NativePacketProtocol.OUYUNC.getProtocol())
                .protocolVersion(NativePacketProtocol.OUYUNC.getProtocolVersion())
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
        MessageHelper.sendClusterInternal(packet, origin, onResult);
    }
}
