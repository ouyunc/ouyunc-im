package com.ouyunc.message.listener;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.model.ImLocalCacheEvictEvent;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.context.RelationLocalCache;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * HTTP 写关系后通过 Redis Pub/Sub 丢掉本节点 Caffeine，避免踢人/禁言/拉黑最多延迟一个 TTL。
 */
final class ImLocalCacheEvictApplier {

    private static final Logger log = LoggerFactory.getLogger(ImLocalCacheEvictApplier.class);

    private ImLocalCacheEvictApplier() {
    }

    static void apply(byte[] body) {
        if (body == null || body.length == 0) {
            return;
        }
        ImLocalCacheEvictEvent event = ImLocalCacheEvictEvent.decode(new String(body, StandardCharsets.UTF_8));
        if (event == null || StringUtils.isAnyBlank(event.getKind(), event.getAppKey())) {
            return;
        }
        try {
            applyEvent(event);
        } catch (Exception e) {
            log.warn("应用 IM 本地缓存失效事件失败 kind={} appKey={}", event.getKind(), event.getAppKey(), e);
        }
    }

    private static void applyEvent(ImLocalCacheEvictEvent event) {
        String kind = event.getKind();
        String appKey = event.getAppKey();
        String groupId = event.getGroupId();
        String userId = event.getUserId();
        String peerId = event.getPeerId();
        if (ImLocalCacheEvictEvent.KIND_GROUP.equals(kind) && StringUtils.isNotBlank(groupId)) {
            MessageContext.groupEntityCache.delete(CacheConstant.buildGroupCacheKey(appKey, groupId));
            MessageContext.groupUserIdentityCache.delete(CacheConstant.buildGroupUserCacheKey(appKey, groupId));
            return;
        }
        if (ImLocalCacheEvictEvent.KIND_GROUP_IDENTITY.equals(kind) && StringUtils.isNotBlank(groupId)) {
            MessageContext.groupUserIdentityCache.delete(CacheConstant.buildGroupUserCacheKey(appKey, groupId));
            return;
        }
        if (ImLocalCacheEvictEvent.KIND_GROUP_MEMBER.equals(kind)
                && StringUtils.isNoneBlank(groupId, userId)) {
            MessageContext.groupUserEntityCache.delete(
                    CacheConstant.buildGroupUserConfigCacheKey(appKey, userId, groupId));
            MessageContext.groupUserIdentityCache.delete(CacheConstant.buildGroupUserCacheKey(appKey, groupId));
            RelationLocalCache.invalidateGroupMember(appKey, groupId, userId);
            return;
        }
        if (ImLocalCacheEvictEvent.KIND_BLACKLIST.equals(kind)
                && StringUtils.isNoneBlank(userId, peerId)) {
            RelationLocalCache.invalidateBlacklist(appKey, userId, peerId);
            return;
        }
        if (ImLocalCacheEvictEvent.KIND_FRIEND.equals(kind)
                && StringUtils.isNoneBlank(userId, peerId)) {
            RelationLocalCache.invalidateFriend(appKey, userId, peerId);
            MessageContext.friendEntityCache.delete(
                    CacheConstant.buildFriendsConfigCacheKey(appKey, userId, peerId));
            MessageContext.friendEntityCache.delete(
                    CacheConstant.buildFriendsConfigCacheKey(appKey, peerId, userId));
            return;
        }
        if (ImLocalCacheEvictEvent.KIND_FRIEND_SHIELD.equals(kind)
                && StringUtils.isNoneBlank(userId, peerId)) {
            RelationLocalCache.invalidateShield(appKey, userId, peerId);
            MessageContext.friendEntityCache.delete(
                    CacheConstant.buildFriendsConfigCacheKey(appKey, userId, peerId));
        }
    }
}
