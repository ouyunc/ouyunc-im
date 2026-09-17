package com.ouyunc.base.model;

import com.ouyunc.base.constant.enums.RelationCacheInvalidateKind;

/**
 * 关系本机缓存失效事件。业务侧 Redis 写完后 PUBLISH 到 IM 节点清 Caffeine。
 * {@code kind} 取值见 {@link RelationCacheInvalidateKind}。
 */
public class RelationCacheInvalidateEvent {

    public static final String KIND_FRIEND_REMOVE = RelationCacheInvalidateKind.FRIEND_REMOVE.name();

    public static final String KIND_FRIEND_ADD = RelationCacheInvalidateKind.FRIEND_ADD.name();

    public static final String KIND_FRIEND_SHIELD = RelationCacheInvalidateKind.FRIEND_SHIELD.name();

    public static final String KIND_BLACKLIST = RelationCacheInvalidateKind.BLACKLIST.name();

    public static final String KIND_GROUP_JOIN = RelationCacheInvalidateKind.GROUP_JOIN.name();

    public static final String KIND_GROUP_QUIT = RelationCacheInvalidateKind.GROUP_QUIT.name();

    public static final String KIND_GROUP_DISSOLVE = RelationCacheInvalidateKind.GROUP_DISSOLVE.name();

    public static final String KIND_GROUP_MEMBER_CONFIG = RelationCacheInvalidateKind.GROUP_MEMBER_CONFIG.name();

    public static final String KIND_GROUP_CONFIG = RelationCacheInvalidateKind.GROUP_CONFIG.name();

    private String kind;

    private String appKey;

    private String userId;

    private String peerId;

    private String groupId;

    /**
     * 屏蔽/拉黑等开关态：true=开启，false=关闭。
     */
    private Boolean enabled;

    public static RelationCacheInvalidateEvent friendRemove(String appKey, String userId, String peerId) {
        RelationCacheInvalidateEvent event = new RelationCacheInvalidateEvent();
        event.kind = KIND_FRIEND_REMOVE;
        event.appKey = appKey;
        event.userId = userId;
        event.peerId = peerId;
        return event;
    }

    public static RelationCacheInvalidateEvent friendAdd(String appKey, String userId, String peerId) {
        RelationCacheInvalidateEvent event = new RelationCacheInvalidateEvent();
        event.kind = KIND_FRIEND_ADD;
        event.appKey = appKey;
        event.userId = userId;
        event.peerId = peerId;
        return event;
    }

    public static RelationCacheInvalidateEvent friendShield(String appKey, String userId, String peerId, boolean shielded) {
        RelationCacheInvalidateEvent event = new RelationCacheInvalidateEvent();
        event.kind = KIND_FRIEND_SHIELD;
        event.appKey = appKey;
        event.userId = userId;
        event.peerId = peerId;
        event.enabled = shielded;
        return event;
    }

    public static RelationCacheInvalidateEvent blacklist(String appKey, String ownerId, String targetId, boolean listed) {
        RelationCacheInvalidateEvent event = new RelationCacheInvalidateEvent();
        event.kind = KIND_BLACKLIST;
        event.appKey = appKey;
        event.userId = ownerId;
        event.peerId = targetId;
        event.enabled = listed;
        return event;
    }

    public static RelationCacheInvalidateEvent groupJoin(String appKey, String groupId, String memberId) {
        RelationCacheInvalidateEvent event = new RelationCacheInvalidateEvent();
        event.kind = KIND_GROUP_JOIN;
        event.appKey = appKey;
        event.groupId = groupId;
        event.userId = memberId;
        return event;
    }

    public static RelationCacheInvalidateEvent groupQuit(String appKey, String groupId, String memberId) {
        RelationCacheInvalidateEvent event = new RelationCacheInvalidateEvent();
        event.kind = KIND_GROUP_QUIT;
        event.appKey = appKey;
        event.groupId = groupId;
        event.userId = memberId;
        return event;
    }

    public static RelationCacheInvalidateEvent groupDissolve(String appKey, String groupId) {
        RelationCacheInvalidateEvent event = new RelationCacheInvalidateEvent();
        event.kind = KIND_GROUP_DISSOLVE;
        event.appKey = appKey;
        event.groupId = groupId;
        return event;
    }

    public static RelationCacheInvalidateEvent groupMemberConfig(String appKey, String groupId, String memberId) {
        RelationCacheInvalidateEvent event = new RelationCacheInvalidateEvent();
        event.kind = KIND_GROUP_MEMBER_CONFIG;
        event.appKey = appKey;
        event.groupId = groupId;
        event.userId = memberId;
        return event;
    }

    public static RelationCacheInvalidateEvent groupConfig(String appKey, String groupId) {
        RelationCacheInvalidateEvent event = new RelationCacheInvalidateEvent();
        event.kind = KIND_GROUP_CONFIG;
        event.appKey = appKey;
        event.groupId = groupId;
        return event;
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

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
