package com.ouyunc.base.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 关系本机缓存失效事件。业务侧 Redis 写完后 HTTP 打到 IM，接入节点清 Caffeine 并集群同步。
 */
public class RelationCacheInvalidateEvent {

    public static final String KIND_FRIEND_REMOVE = "FRIEND_REMOVE";

    public static final String KIND_GROUP_QUIT = "GROUP_QUIT";

    public static final String KIND_GROUP_DISSOLVE = "GROUP_DISSOLVE";

    private String kind;

    private String appKey;

    private String userId;

    private String peerId;

    private String groupId;

    private List<String> memberIds;

    public static RelationCacheInvalidateEvent friendRemove(String appKey, String userId, String peerId) {
        RelationCacheInvalidateEvent event = new RelationCacheInvalidateEvent();
        event.kind = KIND_FRIEND_REMOVE;
        event.appKey = appKey;
        event.userId = userId;
        event.peerId = peerId;
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

    public static RelationCacheInvalidateEvent groupDissolve(String appKey, String groupId, List<String> memberIds) {
        RelationCacheInvalidateEvent event = new RelationCacheInvalidateEvent();
        event.kind = KIND_GROUP_DISSOLVE;
        event.appKey = appKey;
        event.groupId = groupId;
        event.memberIds = memberIds == null ? List.of() : new ArrayList<>(memberIds);
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

    public List<String> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<String> memberIds) {
        this.memberIds = memberIds;
    }
}
