package com.ouyunc.core.relation;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.cache.Cache;
import com.ouyunc.cache.local.caffeine.CaffeineLocalCache;
import com.ouyunc.core.context.MessageContext;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 好友/群成员/拉黑 热路径布尔缓存。未命中再打 Redis；写入关系时主动 mark，避免每条消息 ZSCORE。
 * <p>群成员键带本地 epoch：解散时 bump epoch，无需枚举全员即可使旧布尔键失效。</p>
 */
public final class RelationLocalCache {

    private RelationLocalCache() {
    }

    public static final Cache<String, Boolean> FRIEND = newBooleanCache("relationFriend");
    public static final Cache<String, Boolean> GROUP_MEMBER = newBooleanCache("relationGroupMember");
    public static final Cache<String, Boolean> BLACKLIST = newBooleanCache("relationBlacklist");
    public static final Cache<String, Boolean> SHIELD = newBooleanCache("relationShield");

    /**
     * 本机群成员布尔缓存世代。解散群时递增；键含 epoch，旧世代键自然 miss。
     */
    private static final ConcurrentHashMap<String, AtomicLong> GROUP_MEMBER_EPOCH = new ConcurrentHashMap<>();

    public static String friendKey(String appKey, String ownerId, String peerId) {
        return CacheConstant.buildFriendsCacheKey(appKey, ownerId) + ":" + peerId;
    }

    public static String groupMemberKey(String appKey, String groupId, String memberId) {
        long epoch = currentGroupMemberEpoch(appKey, groupId);
        return CacheConstant.buildGroupUserCacheKey(appKey, groupId) + ":" + memberId + "@" + epoch;
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

    /**
     * 删好友：布尔关系写成 false（避免下一条消息再被误判为好友）+ 删好友配置/屏蔽。
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
     * 好友屏蔽变更：写布尔 + 清配置实体（存在性不变）。
     */
    public static void evictFriendShield(String appKey, String ownerId, String peerId, boolean shielded) {
        if (StringUtils.isAnyBlank(appKey, ownerId, peerId)) {
            return;
        }
        markShield(appKey, ownerId, peerId, shielded);
        MessageContext.friendEntityCache.delete(CacheConstant.buildFriendsConfigCacheKey(appKey, ownerId, peerId));
    }

    /**
     * 拉黑/取消拉黑：写布尔缓存，避免下一条消息沿用旧态。
     */
    public static void evictBlacklist(String appKey, String ownerId, String targetId, boolean listed) {
        if (StringUtils.isAnyBlank(appKey, ownerId, targetId)) {
            return;
        }
        markBlacklist(appKey, ownerId, targetId, listed);
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
     * 群成员配置变更（禁言/成员屏蔽）：只清配置实体，不改在群布尔。
     */
    public static void evictGroupMemberConfig(String appKey, String groupId, String memberId) {
        if (StringUtils.isAnyBlank(appKey, groupId, memberId)) {
            return;
        }
        MessageContext.groupUserEntityCache.delete(CacheConstant.buildGroupUserConfigCacheKey(appKey, memberId, groupId));
    }

    /**
     * 群配置变更（全员禁言等）：只清群实体。
     */
    public static void evictGroupConfig(String appKey, String groupId) {
        if (StringUtils.isAnyBlank(appKey, groupId)) {
            return;
        }
        MessageContext.groupEntityCache.delete(CacheConstant.buildGroupCacheKey(appKey, groupId));
    }

    /**
     * 解散群：bump 本机 epoch（使全员布尔键 miss）+ 清群实体与 identity，无需枚举 memberIds。
     */
    public static void evictGroup(String appKey, String groupId) {
        if (StringUtils.isAnyBlank(appKey, groupId)) {
            return;
        }
        bumpGroupMemberEpoch(appKey, groupId);
        MessageContext.groupEntityCache.delete(CacheConstant.buildGroupCacheKey(appKey, groupId));
        MessageContext.groupUserIdentityCache.delete(CacheConstant.buildGroupUserCacheKey(appKey, groupId));
    }

    private static long currentGroupMemberEpoch(String appKey, String groupId) {
        if (StringUtils.isAnyBlank(appKey, groupId)) {
            return 0L;
        }
        AtomicLong epoch = GROUP_MEMBER_EPOCH.get(epochMapKey(appKey, groupId));
        return epoch == null ? 0L : epoch.get();
    }

    private static long bumpGroupMemberEpoch(String appKey, String groupId) {
        if (StringUtils.isAnyBlank(appKey, groupId)) {
            return 0L;
        }
        return GROUP_MEMBER_EPOCH
                .computeIfAbsent(epochMapKey(appKey, groupId), k -> new AtomicLong(0L))
                .incrementAndGet();
    }

    private static String epochMapKey(String appKey, String groupId) {
        return appKey + ":" + groupId;
    }

    private static Cache<String, Boolean> newBooleanCache(String name) {
        return CaffeineLocalCache.wrap(name, Caffeine.newBuilder()
                .maximumSize(MessageConstant.RELATION_PRESENCE_CACHE_MAX_SIZE)
                .expireAfterWrite(MessageConstant.RELATION_PRESENCE_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS)
                .recordStats()
                .build());
    }
}
