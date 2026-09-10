package com.ouyunc.message.cluster;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.enums.DeviceTypeEnum;
import com.ouyunc.base.constant.enums.NetworkEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageTypeEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.model.RelationCacheInvalidateEvent;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.context.RelationLocalCache;
import com.ouyunc.message.cluster.client.pool.MessageClientPool;
import com.ouyunc.message.cluster.lease.NodeLeaseKeeper;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.protocol.NativePacketProtocol;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本机清关系 Caffeine；集群开启时再经 TCP 同步到其它租约节点（不走 Redis Pub/Sub）。
 */
public final class RelationCacheInvalidateSupport {

    private static final Logger log = LoggerFactory.getLogger(RelationCacheInvalidateSupport.class);

    private RelationCacheInvalidateSupport() {
    }

    public static void applyLocal(RelationCacheInvalidateEvent event) {
        if (event == null || StringUtils.isBlank(event.getKind()) || StringUtils.isBlank(event.getAppKey())) {
            return;
        }
        if (RelationCacheInvalidateEvent.KIND_FRIEND_REMOVE.equals(event.getKind())) {
            RelationLocalCache.evictFriend(event.getAppKey(), event.getUserId(), event.getPeerId());
            return;
        }
        if (RelationCacheInvalidateEvent.KIND_GROUP_QUIT.equals(event.getKind())) {
            RelationLocalCache.evictGroupMember(event.getAppKey(), event.getGroupId(), event.getUserId());
            return;
        }
        if (RelationCacheInvalidateEvent.KIND_GROUP_DISSOLVE.equals(event.getKind())) {
            RelationLocalCache.evictGroup(event.getAppKey(), event.getGroupId(), event.getMemberIds());
        }
    }

    /**
     * HTTP 入口：本机失效后扇出。集群入站只调 {@link #applyLocal}，避免环路。
     */
    public static void applyLocalAndFanout(RelationCacheInvalidateEvent event) {
        applyLocal(event);
        fanoutToCluster(event);
    }

    private static void fanoutToCluster(RelationCacheInvalidateEvent event) {
        if (event == null || !MessageServerContext.serverProperties().isClusterEnable()) {
            return;
        }
        String local = MessageServerContext.serverProperties().getLocalServerAddress();
        String content = JSON.toJSONString(event);
        for (String nodeId : NodeLeaseKeeper.liveLeases().keySet()) {
            if (StringUtils.isBlank(nodeId) || nodeId.equals(local)) {
                continue;
            }
            if (MessageClientPool.ensurePool(nodeId) == null) {
                continue;
            }
            Message message = new Message(
                    MessageContext.idGenerator().generateIdStr(),
                    local,
                    nodeId,
                    OuyuncMessageContentTypeEnum.RELATION_CACHE_INVALIDATE_CONTENT.getType(),
                    content,
                    TimeUtil.currentTimeMillis());
            Packet packet = new Packet(
                    NativePacketProtocol.OUYUNC.getProtocol(),
                    NativePacketProtocol.OUYUNC.getProtocolVersion(),
                    MessageContext.idGenerator().generateId(),
                    DeviceTypeEnum.PC.getType(),
                    NetworkEnum.OTHER.getValue(),
                    Encrypt.SymmetryEncrypt.NONE.getValue(),
                    Serializer.PROTO_STUFF.getValue(),
                    OuyuncMessageTypeEnum.RELATION_CACHE_INVALIDATE.getType(),
                    message);
            try {
                MessageServerContext.findProtocol(packet.getProtocol(), packet.getProtocolVersion())
                        .doSendMessage(packet, nodeId, sendResult -> { });
            } catch (Exception e) {
                log.warn("关系缓存失效集群同步失败 nodeId={} kind={}", nodeId, event.getKind(), e);
            }
        }
    }
}
