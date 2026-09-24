package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ClusterForwardModeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.SendCallback;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.context.MessageServerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 落地本机无连接时，按 Redis 登录 HASH 跟到新节点（S2→S3）。
 * 不改 {@code originServerAddress}，不登记 SERVER QoS 重试。
 */
public final class LoginFollowHelper {

    private static final Logger log = LoggerFactory.getLogger(LoginFollowHelper.class);

    private LoginFollowHelper() {
    }

    public static void followMissed(Packet packet, List<Target> missed) {
        if (packet == null || missed == null || missed.isEmpty()) {
            return;
        }
        for (Target target : missed) {
            tryFollow(packet, target, unused -> { });
        }
    }

    /**
     * @return true 已转到新节点；false 仍在本机/离线/超限，调用方按原路径处理
     */
    public static boolean tryFollow(Packet packet, Target target, SendCallback sendCallback) {
        if (packet == null || packet.getMessage() == null || packet.getMessage().getMetadata() == null
                || target == null || StringUtils.isBlank(target.getTargetIdentity())) {
            return false;
        }
        if (!MessageServerContext.serverProperties().isClusterEnable()) {
            return false;
        }
        Metadata metadata = packet.getMessage().getMetadata();
        if (metadata.getClusterRoute().getLoginFollowHops() >= MessageConstant.LOGIN_FOLLOW_MAX_HOPS) {
            log.warn("登录跟随次数耗尽 packetId={} identity={}", packet.getPacketId(), target.getTargetIdentity());
            return false;
        }
        String appKey = StringUtils.isNotBlank(target.getAppKey()) ? target.getAppKey() : metadata.getIngress().getAppKey();
        LoginClientInfo latest = ClientHelper.onlineDevice(appKey, target.getTargetIdentity(), target.getDeviceType());
        if (latest == null || StringUtils.isBlank(latest.getLoginServerAddress())) {
            return false;
        }
        String local = MessageServerContext.serverProperties().getLocalServerAddress();
        String dest = latest.getLoginServerAddress();
        if (StringUtils.isBlank(local) || dest.equals(local)) {
            return false;
        }
        Packet follow = packet.clone();
        Metadata followMeta = follow.getMessage().getMetadata();
        followMeta.getClusterRoute().setLoginFollowHops(metadata.getClusterRoute().getLoginFollowHops() + 1);
        followMeta.getClusterRoute().setClusterForwardMode(ClusterForwardModeEnum.CLIENT);
        followMeta.getClusterRoute().setFanoutTargets(null);
        Target next = MessageHelper.buildTarget(latest);
        followMeta.getClusterRoute().setTarget(next);
        log.debug("登录跟随 packetId={} identity={} {} -> {}",
                packet.getPacketId(), target.getTargetIdentity(), local, dest);
        MessageHelper.asyncSendMessageWithoutInterceptor(follow, next, sendCallback);
        return true;
    }
}
