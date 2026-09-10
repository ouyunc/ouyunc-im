package com.ouyunc.core.context;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.cache.Cache;
import com.ouyunc.cache.local.caffeine.CaffeineLocalCache;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 好友/群成员/拉黑 热路径布尔缓存。未命中再打 Redis；写入关系时主动 mark，避免每条消息 ZSCORE。
 */
public final class RelationLocalCache {

    private RelationLocalCache() {
    }

    public static final Cache<String, Boolean> FRIEND = newBooleanCache("relationFriend");
    public static final Cache<String, Boolean> GROUP_MEMBER = newBooleanCache("relationGroupMember");
    public static final Cache<String, Boolean> BLACKLIST = newBooleanCache("relationBlacklist");
    public static final Cache<String, Boolean> SHIELD = newBooleanCache("relationShield");

    public static String friendKey(String appKey, String ownerId, String peerId) {
        return CacheConstant.buildFriendsCacheKey(appKey, ownerId) + ":" + peerId;
    }

    public static String groupMemberKey(String appKey, String groupId, String memberId) {
        return CacheConstant.buildGroupUserCacheKey(appKey, groupId) + ":" + memberId;
    }

    public static String blacklistKey(String appKey, String ownerId, String targetId) {
        return CacheConstant.buildBlacklistCacheKey(appKey, ownerId) + ":" + targetId;
    }

    public static void markFriend(String appKey, String userA, String userB, boolean friend) {
        Boolean value = Boolean.valueOf(friend);
        FRIEND.put(friendKey(appKey, userA, userB), value);
        FRIEND.put(friendKey(appKey, userB, userA), value);
    }

    public static void markGroupMember(String appKey, String groupId, String memberId, boolean member) {
        GROUP_MEMBER.put(groupMemberKey(appKey, groupId, memberId), Boolean.valueOf(member));
    }

    public static void markBlacklist(String appKey, String ownerId, String targetId, boolean listed) {
        BLACKLIST.put(blacklistKey(appKey, ownerId, targetId), Boolean.valueOf(listed));
    }

    public static String shieldKey(String appKey, String ownerId, String peerId) {
        return CacheConstant.buildFriendsConfigCacheKey(appKey, ownerId, peerId) + ":shield";
    }

    public static void markShield(String appKey, String ownerId, String peerId, boolean shielded) {
        SHIELD.put(shieldKey(appKey, ownerId, peerId), Boolean.valueOf(shielded));
    }

    public static void invalidateGroupMember(String appKey, String groupId, String memberId) {
        GROUP_MEMBER.delete(groupMemberKey(appKey, groupId, memberId));
    }

    public static void invalidateBlacklist(String appKey, String ownerId, String targetId) {
        BLACKLIST.delete(blacklistKey(appKey, ownerId, targetId));
    }

    public static void invalidateFriend(String appKey, String userA, String userB) {
        FRIEND.delete(friendKey(appKey, userA, userB));
        FRIEND.delete(friendKey(appKey, userB, userA));
    }

    public static void invalidateShield(String appKey, String ownerId, String peerId) {
        SHIELD.delete(shieldKey(appKey, ownerId, peerId));
    }

    /**
     * 删好友：布尔关系写成 false（避免下一条消息再被实体缓存误判为好友）+ 删好友配置/屏蔽。
     */
    public static void evictFriend(String appKey, String userA, String userB) {
        if (StringUtils.isAnyBlank(appKey, userA, userB)) {
            return;
        }
        markFriend(appKey, userA, userB, false);
        markShield(appKey, userA, userB, false);
        markShield(appKey, userB, userA, false);
        MessageContext.friendEntityCache.delete(CacheConstant.buildFriendsConfigCacheKey(appKey, userA, userB));
        MessageContext.friendEntityCache.delete(CacheConstant.buildFriendsConfigCacheKey(appKey, userB, userA));
    }

    /**
     * 退群 / 踢人：成员布尔写成 false + 删成员配置 + 丢弃 identity 列表（避免扇出到已退成员）。
     */
    public static void evictGroupMember(String appKey, String groupId, String memberId) {
        if (StringUtils.isAnyBlank(appKey, groupId, memberId)) {
            return;
        }
        markGroupMember(appKey, groupId, memberId, false);
        MessageContext.groupUserEntityCache.delete(CacheConstant.buildGroupUserConfigCacheKey(appKey, memberId, groupId));
        MessageContext.groupUserIdentityCache.delete(CacheConstant.buildGroupUserCacheKey(appKey, groupId));
    }

    /**
     * 解散群：群实体 + 全员成员缓存 + identity 列表。
     */
    public static void evictGroup(String appKey, String groupId, Iterable<String> memberIds) {
        if (StringUtils.isAnyBlank(appKey, groupId)) {
            return;
        }
        MessageContext.groupEntityCache.delete(CacheConstant.buildGroupCacheKey(appKey, groupId));
        MessageContext.groupUserIdentityCache.delete(CacheConstant.buildGroupUserCacheKey(appKey, groupId));
        if (memberIds == null) {
            return;
        }
        for (String memberId : memberIds) {
            if (StringUtils.isBlank(memberId)) {
                continue;
            }
            markGroupMember(appKey, groupId, memberId, false);
            MessageContext.groupUserEntityCache.delete(CacheConstant.buildGroupUserConfigCacheKey(appKey, memberId, groupId));
        }
    }

    private static Cache<String, Boolean> newBooleanCache(String name) {
        return CaffeineLocalCache.wrap(name, Caffeine.newBuilder()
                .maximumSize(MessageConstant.RELATION_PRESENCE_CACHE_MAX_SIZE)
                .expireAfterWrite(MessageConstant.RELATION_PRESENCE_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS)
                .recordStats()
                .build());
    }
}
