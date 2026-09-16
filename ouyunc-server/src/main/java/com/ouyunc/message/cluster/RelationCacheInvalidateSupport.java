package com.ouyunc.message.cluster;

import com.ouyunc.base.constant.enums.RelationCacheInvalidateKind;
import com.ouyunc.base.model.RelationCacheInvalidateEvent;
import com.ouyunc.core.relation.RelationLocalCache;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本机清关系 Caffeine。由 Redis Pub/Sub 驱动，不再 HTTP 接入、不再集群 TCP 扇出、不 bump Redis 版本。
 */
public final class RelationCacheInvalidateSupport {

    private static final Logger log = LoggerFactory.getLogger(RelationCacheInvalidateSupport.class);

    private RelationCacheInvalidateSupport() {
    }

    /**
     * 应用本机失效；kind 未知或字段不全时静默跳过。
     */
    public static void applyLocal(RelationCacheInvalidateEvent event) {
        if (event == null || StringUtils.isBlank(event.getAppKey())) {
            return;
        }
        RelationCacheInvalidateKind kind = RelationCacheInvalidateKind.from(event.getKind());
        if (kind == null) {
            log.debug("关系缓存失效忽略未知 kind={}", event.getKind());
            return;
        }
        switch (kind) {
            case FRIEND_REMOVE -> {
                if (StringUtils.isAnyBlank(event.getUserId(), event.getPeerId())) {
                    return;
                }
                RelationLocalCache.evictFriend(event.getAppKey(), event.getUserId(), event.getPeerId());
            }
            case FRIEND_SHIELD -> {
                if (StringUtils.isAnyBlank(event.getUserId(), event.getPeerId()) || event.getEnabled() == null) {
                    return;
                }
                RelationLocalCache.evictFriendShield(
                        event.getAppKey(), event.getUserId(), event.getPeerId(), event.getEnabled());
            }
            case BLACKLIST -> {
                if (StringUtils.isAnyBlank(event.getUserId(), event.getPeerId()) || event.getEnabled() == null) {
                    return;
                }
                RelationLocalCache.evictBlacklist(
                        event.getAppKey(), event.getUserId(), event.getPeerId(), event.getEnabled());
            }
            case GROUP_QUIT -> {
                if (StringUtils.isAnyBlank(event.getGroupId(), event.getUserId())) {
                    return;
                }
                RelationLocalCache.evictGroupMember(event.getAppKey(), event.getGroupId(), event.getUserId());
            }
            case GROUP_DISSOLVE -> {
                if (StringUtils.isBlank(event.getGroupId())) {
                    return;
                }
                RelationLocalCache.evictGroup(event.getAppKey(), event.getGroupId());
            }
            case GROUP_MEMBER_CONFIG -> {
                if (StringUtils.isAnyBlank(event.getGroupId(), event.getUserId())) {
                    return;
                }
                RelationLocalCache.evictGroupMemberConfig(
                        event.getAppKey(), event.getGroupId(), event.getUserId());
            }
            case GROUP_CONFIG -> {
                if (StringUtils.isBlank(event.getGroupId())) {
                    return;
                }
                RelationLocalCache.evictGroupConfig(event.getAppKey(), event.getGroupId());
            }
        }
    }
}
