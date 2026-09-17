package com.ouyunc.base.constant.enums;

import org.apache.commons.lang3.StringUtils;

/**
 * 关系本机缓存失效类型。Pub/Sub 必须使用枚举值，避免任意 kind 放大清理范围。
 */
public enum RelationCacheInvalidateKind {

    /** 解除好友：需要 userId、peerId。 */
    FRIEND_REMOVE,

    /** 成为好友：需要 userId、peerId；其它节点把在友布尔写成 true。 */
    FRIEND_ADD,

    /** 好友屏蔽变更：需要 userId、peerId、enabled。 */
    FRIEND_SHIELD,

    /** 拉黑/取消拉黑：需要 userId(owner)、peerId(target)、enabled。 */
    BLACKLIST,

    /** 入群：需要 groupId、userId；清成员列表快照并把在群布尔写成 true。 */
    GROUP_JOIN,

    /** 退群/踢人：需要 groupId、userId。 */
    GROUP_QUIT,

    /** 解散群：需要 groupId；本机 epoch bump，无需 memberIds。 */
    GROUP_DISSOLVE,

    /** 群成员配置变更（禁言/成员级屏蔽等）：需要 groupId、userId，只清实体配置缓存。 */
    GROUP_MEMBER_CONFIG,

    /** 群配置变更（全员禁言等）：需要 groupId，只清群实体缓存。 */
    GROUP_CONFIG;

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
