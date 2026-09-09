package com.ouyunc.base.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * IM 节点本地关系缓存失效事件（Redis Pub/Sub）。
 * <p>编码：{@code kind|appKey|groupId|userId|peerId}，空字段保持占位，字段内禁止 {@code |}。
 */
public class ImLocalCacheEvictEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String KIND_GROUP = "GROUP";
    public static final String KIND_GROUP_IDENTITY = "GROUP_IDENTITY";
    public static final String KIND_GROUP_MEMBER = "GROUP_MEMBER";
    public static final String KIND_BLACKLIST = "BLACKLIST";
    public static final String KIND_FRIEND = "FRIEND";
    public static final String KIND_FRIEND_SHIELD = "FRIEND_SHIELD";

    private static final String SEP = "|";

    private String kind;
    private String appKey;
    private String groupId;
    private String userId;
    private String peerId;

    public ImLocalCacheEvictEvent() {
    }

    public ImLocalCacheEvictEvent(String kind, String appKey, String groupId, String userId, String peerId) {
        this.kind = kind;
        this.appKey = appKey;
        this.groupId = groupId;
        this.userId = userId;
        this.peerId = peerId;
    }

    public static ImLocalCacheEvictEvent group(String appKey, String groupId) {
        return new ImLocalCacheEvictEvent(KIND_GROUP, appKey, groupId, null, null);
    }

    public static ImLocalCacheEvictEvent groupIdentity(String appKey, String groupId) {
        return new ImLocalCacheEvictEvent(KIND_GROUP_IDENTITY, appKey, groupId, null, null);
    }

    public static ImLocalCacheEvictEvent groupMember(String appKey, String groupId, String memberId) {
        return new ImLocalCacheEvictEvent(KIND_GROUP_MEMBER, appKey, groupId, memberId, null);
    }

    public static ImLocalCacheEvictEvent blacklist(String appKey, String ownerId, String targetId) {
        return new ImLocalCacheEvictEvent(KIND_BLACKLIST, appKey, null, ownerId, targetId);
    }

    public static ImLocalCacheEvictEvent friend(String appKey, String userA, String userB) {
        return new ImLocalCacheEvictEvent(KIND_FRIEND, appKey, null, userA, userB);
    }

    public static ImLocalCacheEvictEvent friendShield(String appKey, String ownerId, String peerId) {
        return new ImLocalCacheEvictEvent(KIND_FRIEND_SHIELD, appKey, null, ownerId, peerId);
    }

    public String encode() {
        return nz(kind) + SEP + nz(appKey) + SEP + nz(groupId) + SEP + nz(userId) + SEP + nz(peerId);
    }

    public static ImLocalCacheEvictEvent decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 5) {
            return null;
        }
        ImLocalCacheEvictEvent event = new ImLocalCacheEvictEvent();
        event.kind = emptyToNull(parts[0]);
        event.appKey = emptyToNull(parts[1]);
        event.groupId = emptyToNull(parts[2]);
        event.userId = emptyToNull(parts[3]);
        event.peerId = emptyToNull(parts[4]);
        return event;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPeerId() {
        return peerId;
    }

    public void setPeerId(String peerId) {
        this.peerId = peerId;
    }
}
