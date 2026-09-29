package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ClusterForwardModeEnum;
import com.ouyunc.base.constant.enums.SendStatusEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.SendCallback;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.PacketCopyHelper;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.core.intercept.AbstractMessageInterceptor;
import com.ouyunc.message.cluster.client.pool.MessageClientPool;
import com.ouyunc.message.cluster.lease.NodeLeaseKeeper;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.schedule.QosRetryScheduler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.pool.ChannelPool;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @Author fzx
 * 客户端目标投递、集群内部控制包、当前连接控制响应。
 * 已知 Channel 上的协议转换与写出见 {@link PacketChannelWriter}，业务 Processor 不直接调用它。
 * {@code send} 表示提交到发送链路；最终 {@code writeAndFlush} 由 Netty 异步完成，调用方不选择同步或异步。
 **/
public class MessageSender {

    private static final Logger log = LoggerFactory.getLogger(MessageSender.class);

    /**
     * 多端扇出：按落地节点聚合。本机展开；跨节点一份正文加分批目标。
     * 始发节点在这里登记 QoS，展开后的单目标不再走 {@link #send(Packet, Target)}，避免重复登记。
     */
    public static void send(Packet packet, Collection<LoginClientInfo> loginClientInfos) {
        fanoutToClients(packet, loginClientInfos);
    }



    /**
     * 提交一条客户端业务目标投递。拦截器在这里统一执行，调用方不能绕过。
     * 不把任务再丢进公共发送线程池，避免多目标顺序被二次调度打乱。
     */
    public static void send(Packet packet, Target target) {
        if (CollectionUtils.isEmpty(MessageServerContext.messageInterceptorChain)) {
            doSendMessage(packet, target, sendResult -> { });
            return;
        }
        try {
            for (AbstractMessageInterceptor messageInterceptor : MessageServerContext.messageInterceptorChain) {
                if (!messageInterceptor.preHandle(packet, target)) {
                    log.debug("消息拦截器 {} 拦截了消息: {}", messageInterceptor.getClass().getName(), packet);
                    return;
                }
            }
            doSendMessage(packet, target, sendResult -> { });
            for (AbstractMessageInterceptor messageInterceptor : MessageServerContext.messageInterceptorChain) {
                messageInterceptor.postHandle(packet, target);
            }
        } catch (Exception e) {
            log.error("发送消息过程中发生异常", e);
        }
    }

    /**
     * 已经完成首次业务处理的续传：集群 CLIENT 中转、登录跟随、QoS 重投。
     * 不再跑拦截器，避免重复 QoS 登记或把集群转发当成新的入站消息。
     */
    public static void resumeDelivery(Packet packet, Target target) {
        resumeDelivery(packet, target, sendResult -> { });
    }

    public static void resumeDelivery(Packet packet, Target target, SendCallback sendCallback) {
        doSendMessage(packet, target, sendCallback);
    }

    /**
     * 当前 Channel 上的控制响应：登录成功、心跳 Pong、提交 ACK。
     * 协议从该 Channel 绑定的 Protocol 取得，不按身份再查登录表。
     */
    public static void sendControl(ChannelHandlerContext ctx, Packet packet) {
        sendControl(ctx, packet, sendResult -> { });
    }

    public static void sendControl(ChannelHandlerContext ctx, Packet packet, SendCallback sendCallback) {
        PacketChannelWriter.sendOnChannel(ctx, packet, sendCallback == null ? sendResult -> { } : sendCallback);
    }

    /** 控制响应尽力写出：失败不发布 SEND_FAIL，供受理回执和本机扇出使用。 */
    public static void sendControlQuiet(ChannelHandlerContext ctx, Packet packet) {
        PacketChannelWriter.sendOnChannelBestEffort(ctx, packet);
    }

    public static boolean isChannelSendable(ChannelHandlerContext ctx) {
        return PacketChannelWriter.isSendable(ctx);
    }

    public static Target resolveReplyTarget(ChannelHandlerContext ctx, Packet packet, String messageFrom) {
        return PacketChannelWriter.resolveReplyTarget(ctx, packet, messageFrom);
    }

    /**
     * 集群内部控制包（如 QOS_RETRY_CANCEL）：{@link ClusterForwardModeEnum#INTERNAL}，
     * 直连 dest；失败回溯下一跳，最终节点仍是 dest，落地进 Processor 不写客户端。
     */
    public static void sendClusterInternal(Packet packet, String destServerAddress) {
        sendClusterInternal(packet, destServerAddress, sendResult -> {
            if (sendResult != null && sendResult.getSendStatus() == SendStatusEnum.SEND_FAIL) {
                Throwable cause = sendResult.getException();
                log.warn("集群内部控制包发送失败 type={} packetId={} dest={} cause={}",
                        packet == null ? null : packet.getMessageType(),
                        packet == null ? null : packet.getPacketId(),
                        destServerAddress,
                        cause == null ? null : cause.getMessage());
            }
        });
    }

    /** Processor 与 RouteHandler 共用：只认 Target 落地机，不用 message.to。 */
    public static String clusterDest(Packet packet) {
        if (packet == null || packet.getMessage() == null || packet.getMessage().getMetadata() == null) {
            return null;
        }
        Target target = packet.getMessage().getMetadata().getClusterRoute().getTarget();
        if (target == null) {
            return null;
        }
        String dest = target.getTargetServerAddress();
        return StringUtils.isBlank(dest) ? null : dest;
    }

    public static void sendClusterInternal(Packet packet, String destServerAddress, SendCallback sendCallback) {
        doSendClusterInternal(packet, destServerAddress, sendCallback);
    }


    /**
     * 多端扇出：本机分批展开；远程按节点打包 {@link Metadata#getFanoutTargets()}。
     */
    private static void fanoutToClients(Packet packet, Collection<LoginClientInfo> loginClientInfos) {
        if (packet == null || CollectionUtils.isEmpty(loginClientInfos)) {
            return;
        }
        String local = MessageServerContext.serverProperties().getLocalServerAddress();
        Map<String, List<LoginClientInfo>> byNode = new LinkedHashMap<>();
        for (LoginClientInfo client : loginClientInfos) {
            if (client == null) {
                continue;
            }
            String node = client.getLoginServerAddress();
            if (node == null || node.isBlank()) {
                node = local;
            }
            byNode.computeIfAbsent(node, ignored -> new ArrayList<>()).add(client);
        }
        List<LoginClientInfo> localClients = byNode.remove(local);
        boolean cluster = MessageServerContext.serverProperties().isClusterEnable();
        if (cluster) {
            QosRetryScheduler.scheduleForClients(packet, loginClientInfos);
        } else {
            QosRetryScheduler.scheduleForClients(packet, localClients);
        }
        if (CollectionUtils.isNotEmpty(localClients)) {
            List<Target> targets = new ArrayList<>(localClients.size());
            for (LoginClientInfo c : localClients) {
                targets.add(Target.newBuilder()
                        .appKey(c.getAppKey())
                        .targetIdentity(c.getIdentity())
                        .targetServerAddress(c.getLoginServerAddress())
                        .deviceType(c.getDeviceType())
                        .build());
            }
            ClientHelper.deliverLocalFanoutTargets(packet, targets);
        }
        for (Map.Entry<String, List<LoginClientInfo>> entry : byNode.entrySet()) {
            if (!cluster) {
                log.warn("未开启集群，跳过远端节点投递 node={} size={} packetId={}",
                        entry.getKey(), entry.getValue().size(), packet.getPacketId());
                continue;
            }
            sendRemoteFanoutBatches(packet, entry.getKey(), entry.getValue());
        }
    }

    private static void sendRemoteFanoutBatches(Packet packet, String nodeId,
                                                List<LoginClientInfo> clients) {
        int batch = MessageConstant.GROUP_FANOUT_REMOTE_TARGET_BATCH;
        for (int from = 0; from < clients.size(); from += batch) {
            int to = Math.min(from + batch, clients.size());
            List<Target> targets = new ArrayList<>(to - from);
            for (int i = from; i < to; i++) {
                LoginClientInfo loginClientInfo = clients.get(i);
                targets.add(Target.newBuilder()
                        .appKey(loginClientInfo.getAppKey())
                        .targetIdentity(loginClientInfo.getIdentity())
                        .targetServerAddress(loginClientInfo.getLoginServerAddress())
                        .deviceType(loginClientInfo.getDeviceType())
                        .build());
            }
            Packet fanout = packet.clone();
            Metadata metadata = fanout.getMessage().getMetadata();
            metadata.ensureClusterRoute().setClusterForwardMode(ClusterForwardModeEnum.CLIENT);
            metadata.ensureClusterRoute().setFanoutTargets(targets);
            Target envelope = Target.newBuilder()
                    .appKey(metadata.getIngress().getAppKey())
                    .targetServerAddress(nodeId)
                    .build();
            resumeDelivery(fanout, envelope);
        }
    }


    private static void doSendClusterInternal(Packet originPacket, String destServerAddress,
                                              SendCallback sendCallback) {
        if (originPacket == null || StringUtils.isBlank(destServerAddress)) {
            notifySendFail(originPacket, "集群内部控制包缺少目标节点", sendCallback);
            return;
        }
        if (!MessageServerContext.serverProperties().isClusterEnable()) {
            notifySendFail(originPacket, "未开启集群，无法转发内部控制包", sendCallback);
            return;
        }
        String local = MessageServerContext.serverProperties().getLocalServerAddress();
        if (Objects.equals(local, destServerAddress)) {
            notifySendFail(originPacket, "集群内部控制包目标是本机", sendCallback);
            return;
        }
        Packet packet = PacketCopyHelper.copyForDelivery(originPacket, null);
        Metadata metadata = packet.getMessage() == null ? null : packet.getMessage().getMetadata();
        if (metadata != null) {
            metadata.ensureClusterRoute().setClusterForwardMode(ClusterForwardModeEnum.INTERNAL);
            metadata.ensureClusterRoute().setFanoutTargets(null);
            ensureInternalForwardTarget(metadata, destServerAddress);
        }
        ChannelPool destPool = resolveClusterChannelPool(destServerAddress);
        if (destPool != null) {
            writeViaClusterPool(packet, destPool, destServerAddress, sendCallback);
            return;
        }
        log.warn("集群内部控制包直连不到 {}，尝试下一跳 type={}", destServerAddress, packet.getMessageType());
        relayViaNextHop(packet, destServerAddress, sendCallback);
    }

    private static void ensureInternalForwardTarget(Metadata metadata, String destServerAddress) {
        Target target = metadata.getClusterRoute().getTarget();
        if (target == null) {
            metadata.ensureClusterRoute().setTarget(Target.newBuilder()
                    .appKey(metadata.getIngress().getAppKey())
                    .targetServerAddress(destServerAddress)
                    .build());
            return;
        }
        if (StringUtils.isBlank(target.getTargetServerAddress())) {
            target.setTargetServerAddress(destServerAddress);
        }
    }


    /**
     * 客户端目标投递。集群中 {@link Target#getTargetServerAddress()} 是最终落地机，不可改成下一跳。
     * 本机路径不改原始 Packet；跨节点先 {@link PacketCopyHelper#copyForDelivery} 再写本次 target。
     */
    private static void doSendMessage(Packet originPacket, Target target, SendCallback sendCallback) {
        if (originPacket == null || originPacket.getMessage() == null) {
            notifySendFail(originPacket, "投递包缺少 message", sendCallback);
            return;
        }
        Metadata originMetadata = originPacket.getMessage().getMetadata();
        if (originMetadata == null) {
            notifySendFail(originPacket, "投递包缺少 metadata", sendCallback);
            return;
        }
        if (originMetadata.isInternalForward()) {
            log.error("doSendMessage 禁止覆盖 INTERNAL 为 CLIENT packetId={} dest={}",
                    originPacket.getPacketId(), target == null ? null : target.getTargetServerAddress());
            notifySendFail(originPacket, "内部控制包不得走客户端投递路径", sendCallback);
            return;
        }
        if (target == null) {
            notifySendFail(originPacket, "缺少投递目标", sendCallback);
            return;
        }
        String destServerAddress = target.getTargetServerAddress();
        String localServerAddress = MessageServerContext.serverProperties().getLocalServerAddress();
        boolean cluster = MessageServerContext.serverProperties().isClusterEnable();
        if (!cluster) {
            if (StringUtils.isNotBlank(destServerAddress)
                    && !Objects.equals(localServerAddress, destServerAddress)) {
                notifySendFail(originPacket, "未开启集群，无法投递到远端会话 " + destServerAddress, sendCallback);
                return;
            }
            deliverLocal(originPacket, target, sendCallback);
            return;
        }
        if (Objects.equals(localServerAddress, destServerAddress) || StringUtils.isBlank(destServerAddress)) {
            deliverLocal(originPacket, target, sendCallback);
            return;
        }
        Packet packet = PacketCopyHelper.copyForDelivery(originPacket, target);
        Metadata metadata = packet.getMessage().getMetadata();
        if (!metadata.isClientForward()) {
            metadata.ensureClusterRoute().setClusterForwardMode(ClusterForwardModeEnum.CLIENT);
        }
        ChannelPool destPool = resolveClusterChannelPool(destServerAddress);
        if (destPool != null) {
            writeViaClusterPool(packet, destPool, destServerAddress, sendCallback);
            return;
        }
        log.warn("获取不到消息需要到达的服务: {}，尝试经其他节点中转，最终 dest 不变", destServerAddress);
        relayViaNextHop(packet, destServerAddress, sendCallback);
    }

    /**
     * 本机落地：普通单播按 identity+device 写连接；节点广播只扫本机登录表；聚合扇出按 fanoutTargets 展开。
     */
    private static void deliverLocal(Packet packet, Target target, SendCallback sendCallback) {
        Metadata metadata = packet.getMessage().getMetadata();
        if (metadata != null && CollectionUtils.isNotEmpty(metadata.getClusterRoute().getFanoutTargets())) {
            ClientHelper.deliverLocalFanoutTargets(packet, metadata.getClusterRoute().getFanoutTargets());
            return;
        }
        String identity = target.getTargetIdentity();
        if (metadata != null && metadata.getClusterRoute().isLocalBroadcastOnly()
                && (identity == null || identity.isEmpty())) {
            ClientHelper.deliverLocalBroadcast(target.getAppKey(), packet);
            return;
        }
        if (!hasLocalActiveConnection(target)
                && LoginFollowHelper.tryFollow(packet, target, sendCallback)) {
            return;
        }
        String combo = IdentityUtil.generalComboIdentity(
                target.getAppKey(), target.getTargetIdentity(), target.getDeviceType());
        ChannelHandlerContext ctx = MessageServerContext.localLoginClientRegisterTable.get(combo);
        // 写出用副本，避免 ensureOutboundTarget 或后续编码改到调用方持有的 Packet。
        // 协议仍从本机 Channel 读取，不使用副本上的旧协议字段。
        Packet outbound = PacketCopyHelper.copyForDelivery(packet, target);
        PacketChannelWriter.sendOnChannel(ctx, outbound, wrapRemoteLoginClose(outbound, target, sendCallback));
    }

    private static boolean hasLocalActiveConnection(Target target) {
        if (target == null || StringUtils.isBlank(target.getTargetIdentity())) {
            return false;
        }
        String combo = IdentityUtil.generalComboIdentity(
                target.getAppKey(), target.getTargetIdentity(), target.getDeviceType());
        ChannelHandlerContext ctx = MessageServerContext.localLoginClientRegisterTable.get(combo);
        return PacketChannelWriter.isSendable(ctx);
    }

    /**
     * REMOTE_LOGIN 落地写出后再关旧连接；集群 CLIENT 转发包不进 ServerNotify 处理器，必须挂在写出回调上。
     */
    private static SendCallback wrapRemoteLoginClose(Packet packet, Target target, SendCallback sendCallback) {
        if (!ClientHelper.isRemoteLoginNotify(packet)) {
            return sendCallback;
        }
        return sendResult -> {
            try {
                if (sendCallback != null) {
                    sendCallback.onCallback(sendResult);
                }
            } finally {
                closeLocalTarget(target);
            }
        };
    }

    private static void closeLocalTarget(Target target) {
        if (target == null || StringUtils.isBlank(target.getTargetIdentity())) {
            return;
        }
        String combo = IdentityUtil.generalComboIdentity(
                target.getAppKey(), target.getTargetIdentity(), target.getDeviceType());
        ChannelHandlerContext ctx = MessageServerContext.localLoginClientRegisterTable.get(combo);
        if (ctx != null && ctx.channel() != null && ctx.channel().isActive()) {
            ctx.close();
        }
    }

    /**
     * 只使用已获心跳 ACK 的 active 池；未知节点建池后由后台心跳确认。
     */
    private static ChannelPool resolveClusterChannelPool(String serverAddress) {
        // global 只供心跳探测；业务不得绕过 active 的健康门槛。
        ChannelPool pool = MessageServerContext.clusterActiveServerRegistryTableCache.get(serverAddress);
        if (pool == null
                && MessageServerContext.clusterGlobalServerRegistryTableCache.get(serverAddress) == null
                && NodeLeaseKeeper.hasLiveLease(serverAddress)) {
            MessageClientPool.ensurePool(serverAddress);
        }
        return pool;
    }

    /**
     * 直连 dest 或 hop 失败：route(失败地址) 只选下一跳连接，包上 dest 保持登录机/目标节点。
     */
    private static void relayViaNextHop(Packet packet, String failedServerAddress, SendCallback sendCallback) {
        String nextHop = MessageServerContext.messageRouter.route(packet, failedServerAddress);
        if (nextHop == null) {
            notifySendFail(packet, "消息id: " + packet.getPacketId() + " 尝试路由多次，都没有找到可用的服务！", sendCallback);
            return;
        }
        ChannelPool hopPool = resolveClusterChannelPool(nextHop);
        if (hopPool == null) {
            log.warn("下一跳 {} 无连接池，继续回溯", nextHop);
            relayViaNextHop(packet, nextHop, sendCallback);
            return;
        }
        writeViaClusterPool(packet, hopPool, nextHop, sendCallback);
    }

    /**
     * 经 hop 的集群连接写出；acquire 失败则对该 hop 再回溯，不改 Target.targetServerAddress。
     */
    private static void writeViaClusterPool(Packet packet, ChannelPool channelPool,
                                            String hopServerAddress, SendCallback sendCallback) {
        Future<Channel> channelFuture = channelPool.acquire();
        if (channelFuture == null) {
            log.warn("获取不到消息需要到达的服务连接: {}", hopServerAddress);
            relayViaNextHop(packet, hopServerAddress, sendCallback);
            return;
        }
        Metadata metadata = packet.getMessage().getMetadata();
        channelFuture.addListener((FutureListener<Channel>) acquireFuture -> {
            if (!acquireFuture.isDone()) {
                log.error("发送消息时，获取channel异常！");
                return;
            }
            if (!acquireFuture.isSuccess()) {
                Throwable cause = acquireFuture.cause();
                log.warn("客户端获取channel异常！原因: {}", cause == null ? "" : cause.getMessage());
                relayViaNextHop(packet, hopServerAddress, sendCallback);
                return;
            }
            Channel channel = acquireFuture.getNow();
            if (channel == null) {
                log.error("发送集群消息时，获取channel失败！");
                notifySendFail(packet, "发送集群消息时，获取channel失败！", sendCallback);
                return;
            }
            Integer channelPoolHashCode = ChannelAttrUtil.getChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_TAG_POOL);
            if (channelPoolHashCode == null) {
                ChannelAttrUtil.setChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_TAG_POOL, channelPool.hashCode());
            }
            metadata.ensureClusterRoute().setFromServerAddress(MessageServerContext.serverProperties().getLocalServerAddress());
            Runnable releaseChannel = () -> channelPool.release(channel);
            PacketChannelWriter.runOnEventLoop(channel, packet, sendCallback,
                    () -> PacketChannelWriter.tryWritePacketAndThen(channel, packet, sendCallback, releaseChannel),
                    releaseChannel);
        });
    }

    private static void notifySendFail(Packet packet, Throwable cause, SendCallback sendCallback) {
        PacketChannelWriter.notifySendFail(packet, cause, sendCallback);
    }

    private static void notifySendFail(Packet packet, String message, SendCallback sendCallback) {
        PacketChannelWriter.notifySendFail(packet, message, sendCallback);
    }

}
