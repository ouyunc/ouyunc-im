package com.ouyunc.base.constant.enums;

import org.apache.commons.lang3.StringUtils;

/**
 * 关系本机缓存失效类型。HTTP 与集群同步必须使用枚举值，避免任意 kind 放大清理范围。
 */
public enum RelationCacheInvalidateKind {

    /** 解除好友：需要 userId、peerId。 */
    FRIEND_REMOVE,

    /** 退群/踢人：需要 groupId、userId。 */
    GROUP_QUIT,

    /** 解散群：需要 groupId；memberIds 可选，有则受上限约束。 */
    GROUP_DISSOLVE;

    /**
     * 解析 kind；空白或未知返回 {@code null}。
     */
    public static RelationCacheInvalidateKind from(String kind) {
        if (StringUtils.isBlank(kind)) {
            return null;
        }
        String normalized = kind.trim();
        for (RelationCacheInvalidateKind value : values()) {
            if (value.name().equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        return null;
    }
}
