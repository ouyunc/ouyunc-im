package com.ouyunc.message.cluster;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.DeviceTypeEnum;
import com.ouyunc.base.constant.enums.NetworkEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageTypeEnum;
import com.ouyunc.base.constant.enums.RelationCacheInvalidateKind;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.model.RelationCacheInvalidateEvent;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.context.RelationLocalCache;
import com.ouyunc.message.cluster.client.pool.MessageClientPool;
import com.ouyunc.message.cluster.lease.NodeLeaseKeeper;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.protocol.NativePacketProtocol;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * 本机清关系 Caffeine；集群开启时再经 TCP 同步到其它租约节点（不走 Redis Pub/Sub）。
 */
public final class RelationCacheInvalidateSupport {

    private static final Logger log = LoggerFactory.getLogger(RelationCacheInvalidateSupport.class);

    private RelationCacheInvalidateSupport() {
    }

    /**
     * HTTP 入口校验：kind 必须是枚举，并检查对应字段与 memberIds 上限。
     * 校验通过后会把 kind 规范为枚举名；失败返回错误文案。
     */
    public static String validateForHttp(RelationCacheInvalidateEvent event) {
        if (event == null || StringUtils.isBlank(event.getAppKey())) {
            return "appKey 不能为空";
        }
        RelationCacheInvalidateKind kind = RelationCacheInvalidateKind.from(event.getKind());
        if (kind == null) {
            return "kind 无效，允许: FRIEND_REMOVE / GROUP_QUIT / GROUP_DISSOLVE";
        }
        event.setKind(kind.name());
        switch (kind) {
            case FRIEND_REMOVE -> {
                if (StringUtils.isAnyBlank(event.getUserId(), event.getPeerId())) {
                    return "FRIEND_REMOVE 需要 userId 与 peerId";
                }
            }
            case GROUP_QUIT -> {
                if (StringUtils.isAnyBlank(event.getGroupId(), event.getUserId())) {
                    return "GROUP_QUIT 需要 groupId 与 userId";
                }
            }
            case GROUP_DISSOLVE -> {
                if (StringUtils.isBlank(event.getGroupId())) {
                    return "GROUP_DISSOLVE 需要 groupId";
                }
                List<String> memberIds = event.getMemberIds();
                if (memberIds != null && memberIds.size() > MessageConstant.RELATION_CACHE_MEMBER_IDS_MAX) {
                    return "memberIds 超过上限 " + MessageConstant.RELATION_CACHE_MEMBER_IDS_MAX;
                }
            }
        }
        return null;
    }

    public static void applyLocal(RelationCacheInvalidateEvent event) {
        if (event == null || StringUtils.isBlank(event.getAppKey())) {
            return;
        }
        RelationCacheInvalidateKind kind = RelationCacheInvalidateKind.from(event.getKind());
        if (kind == null) {
            return;
        }
        switch (kind) {
            case FRIEND_REMOVE -> {
                if (StringUtils.isAnyBlank(event.getUserId(), event.getPeerId())) {
                    return;
                }
                RelationLocalCache.evictFriend(event.getAppKey(), event.getUserId(), event.getPeerId());
            }
            case GROUP_QUIT -> {
                if (StringUtils.isAnyBlank(event.getGroupId(), event.getUserId())) {
                    return;
                }
                RelationLocalCache.evictGroupMember(event.getAppKey(), event.getGroupId(), event.getUserId());
                bumpGroupRelationVersion(event.getAppKey(), event.getGroupId());
            }
            case GROUP_DISSOLVE -> {
                if (StringUtils.isBlank(event.getGroupId())) {
                    return;
                }
                List<String> memberIds = event.getMemberIds();
                if (memberIds != null && memberIds.size() > MessageConstant.RELATION_CACHE_MEMBER_IDS_MAX) {
                    log.warn("关系缓存失效丢弃超限 memberIds appKey={} groupId={} size={}",
                            event.getAppKey(), event.getGroupId(), memberIds.size());
                    return;
                }
                RelationLocalCache.evictGroup(event.getAppKey(), event.getGroupId(), memberIds);
                bumpGroupRelationVersion(event.getAppKey(), event.getGroupId());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void bumpGroupRelationVersion(String appKey, String groupId) {
        if (StringUtils.isAnyBlank(appKey, groupId)) {
            return;
        }
        try {
            StringRedisTemplate redis = CacheFactory.STRING_REDIS.instance();
            redis.opsForValue().increment(CacheConstant.buildGroupRelationVersionCacheKey(appKey, groupId));
        } catch (Exception e) {
            log.warn("递增群关系版本失败 appKey={} groupId={}", appKey, groupId, e);
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
