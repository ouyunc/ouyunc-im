package com.ouyunc.message.schedule;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.enums.ClusterForwardModeEnum;
import com.ouyunc.base.constant.enums.DeviceTypeEnum;
import com.ouyunc.base.constant.enums.NetworkEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageTypeEnum;
import com.ouyunc.base.constant.enums.QosLevelEnum;
import com.ouyunc.base.constant.enums.QosModeEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
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
 */
public final class QosRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(QosRetryScheduler.class);

    private QosRetryScheduler() {
    }

    /** 首次业务处理机盖章；集群中转不得调用，避免改成落地节点。 */
    public static void stampOrigin(Packet packet) {
        if (packet == null || packet.getMessage() == null) {
            return;
        }
        String local = MessageServerContext.serverProperties().getLocalServerAddress();
        if (StringUtils.isBlank(local)) {
            return;
        }
        packet.getMessage().getMetadata().setOriginServerAddress(local);
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
        if (StringUtils.isBlank(message.getMetadata().getOriginServerAddress())) {
            stampOrigin(packet);
        }
        if (!isOriginNode(packet)) {
            return;
        }
        String appKey = message.getMetadata().getAppKey();
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
     * C2S ACK：本机有任务则取消；否则按热存储 originServerAddress 转给始发节点。
     * 不给 to 回 S2C：C2S 丢失时由始发节点继续下行重试即可自愈。
     */
    public static void onClientAck(LoginClientInfo login, long packetId) {
        if (!retryEnabled() || login == null || packetId <= 0) {
            return;
        }
        if (StringUtils.isAnyBlank(login.getAppKey(), login.getIdentity())) {
            log.warn("QoS ACK 缺少已认证登录身份，忽略取消重试");
            return;
        }
        String taskId = QosRetryTaskIds.build(login.getAppKey(), packetId, login.getIdentity(), login.getDeviceType());
        if (StringUtils.isBlank(taskId)) {
            return;
        }
        if (ScheduleTimer.cancelIfPresent(taskId)) {
            return;
        }
        String origin = resolveOrigin(login.getAppKey(), packetId);
        String local = MessageServerContext.serverProperties().getLocalServerAddress();
        if (StringUtils.isBlank(origin) || origin.equals(local)) {
            return;
        }
        if (!MessageServerContext.serverProperties().isClusterEnable()
                || !NodeLeaseKeeper.hasLiveLease(origin)) {
            log.warn("QoS C2S ACK 无法转回始发节点 origin={} packetId={}", origin, packetId);
            return;
        }
        forwardCancel(origin, new QosRetryCancelContent(
                login.getAppKey(), packetId, login.getIdentity(), login.getDeviceType()));
    }

    public static void onClusterCancel(QosRetryCancelContent content) {
        if (content == null || content.getPacketId() <= 0
                || StringUtils.isAnyBlank(content.getAppKey(), content.getIdentity())) {
            return;
        }
        String taskId = QosRetryTaskIds.build(
                content.getAppKey(), content.getPacketId(), content.getIdentity(), content.getDeviceType());
        if (StringUtils.isBlank(taskId)) {
            return;
        }
        ScheduleTimer.cancelIfPresent(taskId);
    }

    private static boolean isOriginNode(Packet packet) {
        String local = MessageServerContext.serverProperties().getLocalServerAddress();
        if (StringUtils.isBlank(local) || packet.getMessage() == null
                || packet.getMessage().getMetadata() == null) {
            return false;
        }
        Metadata metadata = packet.getMessage().getMetadata();
        if (StringUtils.isNotBlank(metadata.getOriginServerAddress())) {
            return local.equals(metadata.getOriginServerAddress());
        }
        return !metadata.isClientForward();
    }

    private static void retryOnce(QosRetryTaskContext retryContext, String taskId, TimerTaskWrapper taskWrapper) {
        LoginClientInfo device = ClientHelper.onlineDevice(
                retryContext.targetAppKey(), retryContext.targetIdentity(), retryContext.deviceType());
        if (device == null) {
            // 离线只跳过本轮，任务保留到 C2S 或达到最大次数
            return;
        }
        Packet schedulePackage = loadRetryPacket(retryContext);
        if (schedulePackage == null) {
            log.warn("QoS 重试加载消息失败，取消任务: taskId={}", taskId);
            taskWrapper.cancel();
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

    private static String resolveOrigin(String appKey, long packetId) {
        Packet stored = loadStoredPacket(appKey, packetId);
        if (stored == null || stored.getMessage() == null || stored.getMessage().getMetadata() == null) {
            return null;
        }
        return stored.getMessage().getMetadata().getOriginServerAddress();
    }

    private static void forwardCancel(String origin, QosRetryCancelContent content) {
        String local = MessageServerContext.serverProperties().getLocalServerAddress();
        long now = TimeUtil.currentTimeMillis();
        Metadata metadata = new Metadata();
        metadata.setAppKey(content.getAppKey());
        metadata.setServerTime(now);
        metadata.setFromServerAddress(local);
        metadata.setClusterForwardMode(ClusterForwardModeEnum.INTERNAL);
        metadata.setTarget(Target.newBuilder()
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
        // INTERNAL：直连 origin 失败则下一跳，落地进 Processor 不写客户端
        MessageHelper.sendClusterInternal(packet, origin);
    }
}
