package com.ouyunc.core.context;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.cache.Cache;
import com.ouyunc.cache.local.caffeine.CaffeineLocalCache;

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

    private static Cache<String, Boolean> newBooleanCache(String name) {
        return CaffeineLocalCache.wrap(name, Caffeine.newBuilder()
                .maximumSize(MessageConstant.RELATION_PRESENCE_CACHE_MAX_SIZE)
                .expireAfterWrite(MessageConstant.RELATION_PRESENCE_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS)
                .recordStats()
                .build());
    }
}
