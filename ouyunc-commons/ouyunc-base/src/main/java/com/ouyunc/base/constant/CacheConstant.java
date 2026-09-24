package com.ouyunc.base.constant;

import com.ouyunc.base.utils.IdentityUtil;

/**
 * @Author fzx
 * @Description: 缓存相关常量类 - Redis集群优化版
 **/
public class CacheConstant {

    /***
     * 冒号
     */
    private static final String COLON  = ":";
    
    /***
     * 哈希标签 - 用于Redis集群确保相关数据在同一个slot
     */
    private static final String HASH_TAG_START = "{";
    private static final String HASH_TAG_END = "}";

    /***
     * ouyunc 公共前缀
     */
    private static final String OUYUNC = MessageConstant.OUYUNC + COLON;

    // ============================================ 基础常量定义 ============================================
    
    /***
     * 所有的appKey
     */
    private static final String APP_KEYS = "app-keys";

    /***
     * 平台的 唯一标识key 公共前缀
     */
    private static final String APP_KEY = "app:";

    /***
     * appKey 下的identity 的 客户端信息
     */
    private static final String CLIENT_INFO = "client:";

    /***
     * 消息缓存公共前缀
     */
    private static final String MESSAGE = "msg:";

    /**
     * 消息撤回单调标记，与 {@code msg:} 同槽；只增不减。
     */
    private static final String MESSAGE_WITHDRAWN = "msg-withdrawn:";

    /***
     * 会话已读消息偏移量缓存公共前缀
     */
    private static final String SESSION_READ_MESSAGE_OFFSET = "session-read:";

    /***
     * 锁
     */
    private static final String LOCK = "lock:";

    /** 登录详情 String */
    private static final String IM_LOGIN = "im:login:";

    /** 身份路由 HASH */
    private static final String IM_ROUTE = "im:route:";

    /** 进程租约 */
    private static final String IM_NODE = "im:node:";

    /** 登记节点 id 集合 */
    private static final String IM_NODES = "im:nodes";

    /** 节点连接数 HASH */
    private static final String IM_CONN = "im:conn:";

    /** appKey 连接配额 HASH */
    private static final String IM_QUOTA = "im:quota:";

    /***
     * 用户
     */
    private static final String USER = "user:";

    /***
     * 群组绑定的用户
     */
    private static final String GROUP_USERS = "group-members:";

    /**
     * 群成员 ZSET 完整性标记，与 {@code group-members:} 同槽；禁止把哨兵写进 ZSET member。
     * 值为成员数，须与 ZCARD 一致；不一致则删标记并回源。
     */
    private static final String GROUP_USERS_INIT = "group-members-init:";

    /** 群成员关系版本（回源 CAS） */
    private static final String GROUP_RELATION_VERSION = "group-relation-ver:";

    /***
     * 群成员的信息配置
     */
    private static final String GROUP_USERS_CONFIG = "group-member-cfg:";

    /**
     * 群成员屏蔽索引 Hash（field=memberId）。完整性用旁边的 {@code group-shield-init:} STRING，不往 Hash 里塞哨兵。
     */
    private static final String GROUP_USERS_SHIELD = "group-shield:";

    /** 群屏蔽索引完整性标记，与 group-shield: 同槽 */
    private static final String GROUP_USERS_SHIELD_INIT = "group-shield-init:";

    /***
     * 好友列表
     */
    private static final String FRIENDS = "friends:";

    /**
     * 好友 ZSET 与库一致的标记，与 {@code friends:} 同槽；禁止把哨兵写进 ZSET member。
     * 值为名单基数（完整为正、截断为负），须与 ZCARD 一致。
     */
    private static final String FRIENDS_INIT = "friends-init:";

    /** 好友关系版本（回源 CAS），与 friends:/friends-init: 同槽 */
    private static final String FRIENDS_RELATION_VERSION = "friends-relation-ver:";

    /***
     * 配置， 我的好友信息的配置
     */
    private static final String FRIENDS_CONFIG = "friend-cfg:";

    /***
     * 用户-群列表
     */
    private static final String GROUPS = "groups:";

    /**
     * 用户已加入群 ZSET 完整性标记，与 {@code groups:} 同槽。
     */
    private static final String USER_GROUPS_INIT = "groups-init:";

    /** 用户加群关系版本（回源 CAS），与 groups:/groups-init: 同槽 {@code {appKey:userId}} */
    private static final String USER_GROUPS_RELATION_VERSION = "groups-relation-ver:";

    /***
     * 群
     */
    private static final String GROUP = "group:";

    /***
     * 黑名单 Hash（field=被拉黑人）。完整性用旁边的 {@code blacklist-init:} STRING。
     */
    private static final String BLACKLIST = "blacklist:";

    /** 黑名单 Hash 完整性标记，与 blacklist: 同槽 */
    private static final String BLACKLIST_INIT = "blacklist-init:";

    /**
     * 关系名单回源临时 ZSET 后缀，必须接在已含 hash tag 的 zset key 后以同槽。
     */
    private static final String RELATION_ROSTER_TMP = "tmp";

    /***
     * QoS 幂等
     */
    private static final String QOS_IDEM = "qos:idem:";

    /***
     * http push 幂等
     */
    private static final String HTTP_PUSH_IDEM = ":http-push:idem:";

    /***
     * QoS 幂等 pkt
     */
    private static final String QOS_IDEM_PKT = "pkt:";

    /***
     * QoS 幂等 cli
     */
    private static final String QOS_IDEM_CLI = "client-msg:";

    /***
     * 会话
     */
    private static final String SESSION = "session:";

    /***
     * 聊天会话
     */
    private static final String CHAT_SESSION = "chat-session:";

    /***
     * 好友请求
     */
    private static final String FRIEND_REQUEST = "friend-req:";

    /***
     * 正在处理中的好友请求会话标识
     */
    private static final String FRIEND_REQUEST_SESSION = "friend-req-session:";

    /** 外渠下行已发出但 broker 尚未确认。确认成功后删除，QoS 重试据此补投。 */
    private static final String EXTERNAL_DELIVERY_PENDING = "external-pending:";

    /***
     * 正在处理中的群请求会话标识
     */
    private static final String GROUP_REQUEST_SESSION = "group-req-session:";

    /***
     * 群请求
     */
    private static final String GROUP_REQUEST = "group-req:";

    /***
     * 设备 类型device-type
     */
    private static final String DEVICE_TYPE = "device-type";

    /***
     * 最后一条消息
     */
    private static final String LAST_MESSAGE = "last-msg";

    /** 用户设备单聊未读 Hash 前缀（群聊不在此存储） */
    private static final String USER_DEVICE_UNREAD = "unread:";

    /** 用户设备单聊未读 packetId 集合前缀（有序清除用，member=packetId 十进制串） */
    private static final String USER_DEVICE_UNREAD_IDS = "unread-ids:";

    // ============================================ 集群优化方法 ============================================

    /**
     * 为集群环境构建哈希标签 - Cluster 只认 key 中<strong>第一个</strong>{@code {...}} 为槽。
     */
    private static String withHashTag(String key) {
        return HASH_TAG_START + key + HASH_TAG_END;
    }

    /**
     * 原子聚合槽标签：{@code {appKey:aggregateId}}，会话/用户/群等按边界分片，避免大租户单槽热点。
     */
    private static String withAggregateHashTag(String appKey, String aggregateId) {
        String ak = sanitizeAppKeyToken(appKey);
        String agg = stripHashTagChars(aggregateId == null ? "" : aggregateId.trim());
        return withHashTag(ak + MessageConstant.COLON + agg);
    }

    /**
     * 租户级前缀（仅适合真正的 app 全局小集合，如设备类型表）。
     * QoS 幂等已按 identity/packetId 分片，见 {@link #buildQosIdempotencyPacketKey}。
     * 普通会话/收件箱/群数据请用 {@link #buildAggregateCacheKey}。
     */
    private static String buildBaseCacheKey(String appKey) {
        return OUYUNC + APP_KEY + withHashTag(sanitizeAppKeyToken(appKey)) + COLON;
    }

    /**
     * 按聚合实体分片的业务 key 前缀：首 tag 为 {@code {appKey:aggregateId}}。
     */
    private static String buildAggregateCacheKey(String appKey, String aggregateId) {
        return OUYUNC + APP_KEY + withAggregateHashTag(appKey, aggregateId) + COLON;
    }

    // ============================================ 分布式锁 ============================================

    /**
     * 构建appKey identity 关闭连接的分布式锁key - 集群优化
     */
    public static String buildIdentityBindOrUnbindLockCacheKey(String appKey, String comboIdentity) {
        String identity = IdentityUtil.revertIdentity(comboIdentity);
        return OUYUNC + LOCK + withHashTag(stripHashTagChars(identity)) + COLON + APP_KEY + appKey + COLON + comboIdentity;
    }

    /**
     * 好友请求锁（P2）：首 tag {@code {appKey:sessionId}}，按会话分片，避免租户单槽热点。
     */
    public static String buildFriendRequestLockCacheKey(String appKey, String sessionId) {
        return OUYUNC + LOCK + withAggregateHashTag(appKey, sessionId) + COLON + FRIEND_REQUEST;
    }

    /**
     * 审批处理权：{@code appKey + requestSessionId}，不含用户设备。手机和 PC 必须抢同一把状态。
     */
    public static String buildApprovalProgressCacheKey(String appKey, String requestSessionId) {
        return OUYUNC + LOCK + withAggregateHashTag(appKey, requestSessionId) + COLON + "approval:";
    }

    /**
     * 群请求锁（P2）：首 tag {@code {appKey:sessionId}}；joiner 仅作后缀、不再套多余 hash tag。
     */
    public static String buildGroupRequestLockCacheKey(String appKey, String joiner, String sessionId) {
        return OUYUNC + LOCK + withAggregateHashTag(appKey, sessionId) + COLON + GROUP_REQUEST
                + COLON + stripHashTagChars(joiner == null ? "" : joiner);
    }

    // ============================================ 业务缓存键 ============================================

    /**
     * 构建appKey 下所有设备类型 缓存key - 集群优化
     */
    public static String buildAppKeyDeviceTypeCacheKey(String appKey) {
        return buildBaseCacheKey(appKey) + DEVICE_TYPE;
    }

    /**
     * 构建所有appKeyEntity 缓存key
     */
    public static String buildAppKeysCacheKey() {
        return OUYUNC + APP_KEYS;
    }

    /**
     * 构建appKey 下的identity 的远端客户端设置信息 - 集群优化
     */
    public static String buildRemoteClientInfoCacheKey(String appKey, String identity) {
        return buildAggregateCacheKey(appKey, identity) + CLIENT_INFO;
    }

    /**
     * 构建 本地客户端信息设置 cache key - 集群优化
     */
    public static String buildLocalClientInfoCacheKey(String appKey, String identity) {
        return appKey + COLON + withHashTag(identity);
    }

    /**
     * 消息正文：按 packetId 分片；Cluster 下批量取用逐 key GET，不依赖同槽 MGET。
     */
    public static String buildMessageCacheKey(String appKey, Long packetId) {
        return buildAggregateCacheKey(appKey, String.valueOf(packetId)) + MESSAGE;
    }

    /**
     * 撤回权威标记（STRING=1）。与正文同槽，读路径叠加 retain，回填不得覆盖。
     */
    public static String buildMessageWithdrawnCacheKey(String appKey, Long packetId) {
        return buildAggregateCacheKey(appKey, String.valueOf(packetId)) + MESSAGE_WITHDRAWN;
    }

    /**
     * 会话已读偏移：槽跟收件人（from）收件箱一致，与 unread/unread-ids Lua 同槽。
     */
    public static String buildSessionReadMessageOffsetCacheKey(String appKey, Integer identityType,
                                                             String from, Byte deviceType, String to) {
        String peer = stripHashTagChars(to == null ? "" : to.trim());
        return buildAggregateCacheKey(appKey, from) + SESSION_READ_MESSAGE_OFFSET + identityType + COLON
                + peer + COLON + deviceType;
    }

    /**
     * 登录详情 String。哈希标签为 identity，与路由 HASH 同槽。
     */
    public static String buildLoginCacheKey(String appKey, String comboIdentity) {
        String identity = IdentityUtil.revertIdentity(comboIdentity);
        Byte deviceType = IdentityUtil.revertDeviceType(comboIdentity);
        return OUYUNC + IM_LOGIN + withHashTag(stripHashTagChars(identity)) + COLON
                + sanitizeAppKeyToken(appKey) + COLON + deviceType;
    }

    /**
     * 身份路由 HASH：field=deviceType，value=nodeId|epoch。探测/多端在线看这把 key。
     * 首 tag 必须与 {@link #buildLoginCacheKey} 同一 identity，否则 Cluster CROSSSLOT。
     */
    public static String buildLoginRouteCacheKey(String appKey, String identity) {
        return OUYUNC + IM_ROUTE + withHashTag(stripHashTagChars(identity == null ? "" : identity))
                + COLON + sanitizeAppKeyToken(appKey);
    }

    /**
     * IM 进程租约：value=epoch 字符串，PX 由心跳刷新。
     */
    public static String buildImNodeLeaseCacheKey(String nodeId) {
        return OUYUNC + IM_NODE + withHashTag(nodeId);
    }

    /**
     * 当前登记过的 IM 节点 id 集合（小 SET，心跳 SADD）。
     */
    public static String buildImNodeSetCacheKey() {
        return OUYUNC + IM_NODES;
    }

    /**
     * 节点连接数 HASH：field=appKey，value=count。与租约同 {@code {nodeId}} 槽，由心跳全量覆盖，不跟登录 Lua 同槽。
     */
    public static String buildImNodeConnHashCacheKey(String nodeId) {
        return OUYUNC + IM_CONN + withHashTag(nodeId);
    }

    /**
     * appKey 连接配额 HASH：field=nodeId，value=count。标签 {@code {appKey}}，各节点 Lua 求和预占同一槽。
     */
    public static String buildAppKeyConnQuotaHashCacheKey(String appKey) {
        return OUYUNC + IM_QUOTA + withHashTag(sanitizeAppKeyToken(appKey));
    }

    /**
     * 配额 HASH 扫描模式。本机连接为 0 时仍要扫到死节点残留 field。
     */
    public static String appKeyConnQuotaKeyPattern() {
        return OUYUNC + IM_QUOTA + "*";
    }

    /**
     * 构建 user 用户 cache key - 集群优化
     */
    public static String buildUserCacheKey(String appKey, String identity) {
        return buildAggregateCacheKey(appKey, identity) + USER;
    }

    /**
     * 群成员 ZSET：槽 {@code {appKey:groupId}}，与 group-relation-ver/group-shield/group-members-init 同槽。
     */
    public static String buildGroupUserCacheKey(String appKey, String groupId) {
        return buildAggregateCacheKey(appKey, groupId) + GROUP_USERS;
    }

    /**
     * 群成员名单已完整灌入 Redis 的标记 STRING，与成员 ZSET 同槽。
     */
    public static String buildGroupUserInitCacheKey(String appKey, String groupId) {
        return buildAggregateCacheKey(appKey, groupId) + GROUP_USERS_INIT;
    }

    /**
     * 构建 群组成员在群中的配置信息 cache key - 集群优化
     */
    public static String buildGroupUserConfigCacheKey(String appKey, String memberId, String groupId) {
        return buildAggregateCacheKey(appKey, groupId) + GROUP_USERS_CONFIG + stripHashTagChars(memberId);
    }

    /**
     * 群屏蔽成员 Hash，与成员 ZSET / group-relation-ver / group-shield-init 同 {@code {appKey:groupId}} 槽。
     */
    public static String buildGroupShieldCacheKey(String appKey, String groupId) {
        return buildAggregateCacheKey(appKey, groupId) + GROUP_USERS_SHIELD;
    }

    /**
     * 群屏蔽索引已完整：STRING 存在即表示 Hash 可当权威（空 Hash = 无人屏蔽）。
     */
    public static String buildGroupShieldInitCacheKey(String appKey, String groupId) {
        return buildAggregateCacheKey(appKey, groupId) + GROUP_USERS_SHIELD_INIT;
    }

    /**
     * 构建 好友关系 cache key：按用户分片
     */
    public static String buildFriendsCacheKey(String appKey, String identity) {
        return buildAggregateCacheKey(appKey, identity) + FRIENDS;
    }

    /**
     * 好友名单已与 MySQL 对齐的标记 STRING，与好友 ZSET 同槽。
     */
    public static String buildFriendsInitCacheKey(String appKey, String identity) {
        return buildAggregateCacheKey(appKey, identity) + FRIENDS_INIT;
    }

    /**
     * 好友关系版本：与 friends:/friends-init: 同 {@code {appKey:identity}} 槽。
     */
    public static String buildFriendsRelationVersionCacheKey(String appKey, String identity) {
        return buildAggregateCacheKey(appKey, identity) + FRIENDS_RELATION_VERSION;
    }

    /**
     * 好友配置：槽按 from_to 对
     */
    public static String buildFriendsConfigCacheKey(String appKey, String from, String to) {
        String pair = stripHashTagChars(from) + MessageConstant.UNDERLINE + stripHashTagChars(to);
        return buildAggregateCacheKey(appKey, pair) + FRIENDS_CONFIG;
    }

    /**
     * 用户所加入的群组：按用户分片
     */
    public static String buildUserGroupsCacheKey(String appKey, String userId) {
        return buildAggregateCacheKey(appKey, userId) + GROUPS;
    }

    /**
     * 用户已加入群名单已完整灌入 Redis 的标记 STRING，与 user-groups ZSET 同槽。
     */
    public static String buildUserGroupsInitCacheKey(String appKey, String userId) {
        return buildAggregateCacheKey(appKey, userId) + USER_GROUPS_INIT;
    }

    /**
     * 用户加群关系版本：与 groups:/groups-init: 同 {@code {appKey:userId}} 槽。
     */
    public static String buildUserGroupsRelationVersionCacheKey(String appKey, String userId) {
        return buildAggregateCacheKey(appKey, userId) + USER_GROUPS_RELATION_VERSION;
    }

    /**
     * 群组信息：槽 {@code {appKey:groupId}}
     */
    public static String buildGroupCacheKey(String appKey, String groupId) {
        return buildAggregateCacheKey(appKey, groupId) + GROUP;
    }

    /**
     * identity 黑名单：按用户分片
     */
    public static String buildBlacklistCacheKey(String appKey, String identity) {
        return buildAggregateCacheKey(appKey, identity) + BLACKLIST;
    }

    /**
     * 黑名单 Hash 已完整：STRING 存在即可把 HGET miss 当未拉黑（空 Hash = 无人拉黑）。
     */
    public static String buildBlacklistInitCacheKey(String appKey, String identity) {
        return buildAggregateCacheKey(appKey, identity) + BLACKLIST_INIT;
    }

    /**
     * 关系名单回源临时 ZSET，与正式 zset 同槽。
     */
    public static String buildRelationRosterTmpCacheKey(String zsetKey) {
        if (zsetKey == null || zsetKey.isBlank()) {
            return zsetKey;
        }
        return zsetKey + RELATION_ROSTER_TMP;
    }

    /**
     * QoS 幂等 packet（P1）：有 loginIdentity 时与 client key 同槽 {@code {appKey:identity}}；
     * 仅 packet 维度时按 packetId 分片，避免大租户单槽打爆。
     */
    public static String buildQosIdempotencyPacketKey(String appKey, String loginIdentity, long packetId) {
        String shard = (loginIdentity != null && !loginIdentity.isBlank())
                ? loginIdentity
                : ("pkt-" + packetId);
        return buildAggregateCacheKey(appKey, shard) + QOS_IDEM + QOS_IDEM_PKT
                + stripHashTagChars(String.valueOf(packetId));
    }

    /**
     * QoS 幂等 client：槽 {@code {appKey:loginIdentity}}，与同身份 packet 键同 Lua。
     */
    public static String buildQosIdempotencyClientKey(String appKey, String loginIdentity, String clientMessageId) {
        return buildAggregateCacheKey(appKey, loginIdentity) + QOS_IDEM + QOS_IDEM_CLI
                + stripHashTagChars(loginIdentity) + COLON + clientMessageId;
    }

    /**
     * 会话：槽 {@code {appKey:sessionId}}
     */
    public static String buildSessionCacheKey(String appKey, String sessionId) {
        return buildAggregateCacheKey(appKey, sessionId) + SESSION;
    }

    /**
     * 好友请求会话：槽按业务 sessionId
     */
    public static String buildFriendRequestSessionCacheKey(String appKey, String sessionId, String friendRequestSessionId) {
        return buildAggregateCacheKey(appKey, sessionId) + FRIEND_REQUEST + SESSION
                + COLON + stripHashTagChars(friendRequestSessionId);
    }

    /**
     * 好友请求：槽按 from_to
     */
    public static String buildExternalDeliveryPendingKey(String appKey, String messageId) {
        return buildAggregateCacheKey(appKey, messageId) + EXTERNAL_DELIVERY_PENDING;
    }

    public static String buildFriendRequestCacheKey(String appKey, String from, String to) {
        String pair = stripHashTagChars(from) + MessageConstant.UNDERLINE + stripHashTagChars(to);
        return buildAggregateCacheKey(appKey, pair) + FRIEND_REQUEST_SESSION;
    }

    /**
     * 群请求会话：槽按 joiner
     */
    public static String buildGroupRequestSessionCacheKey(String appKey, String joiner, String groupRequestSessionId) {
        return buildAggregateCacheKey(appKey, joiner) + GROUP_REQUEST + SESSION
                + COLON + stripHashTagChars(groupRequestSessionId);
    }

    /**
     * 群请求：槽按 groupId
     */
    public static String buildGroupRequestCacheKey(String appKey, String joiner, String groupId) {
        return buildAggregateCacheKey(appKey, groupId) + GROUP_REQUEST_SESSION
                + COLON + stripHashTagChars(joiner);
    }

    /**
     * 会话最后一条消息：与 session 同槽
     */
    public static String buildSessionLastMessageCacheKey(String appKey, String sessionId) {
        return buildAggregateCacheKey(appKey, sessionId) + SESSION + COLON + LAST_MESSAGE;
    }

    /**
     * 聊天会话列表：按用户分片
     */
    public static String buildChatSessionCacheKey(String appKey, String identity, Byte deviceType) {
        return buildAggregateCacheKey(appKey, identity) + CHAT_SESSION + COLON + deviceType;
    }

    /**
     * 会话读模型 Hash：{@code ouyunc:app:appKey:session-view:userId:deviceType}。
     * appKey 不加 hash tag，与客服 ticket 维 key 一致；field=对端 id。
     */
    public static String buildSessionViewCacheKey(String appKey, String userId, Byte deviceType) {
        return buildCsTicketKeyPrefix(appKey) + "session-view:" + stripHashTagChars(userId == null ? "" : userId)
                + COLON + deviceType;
    }

    /** IM 用户翻译偏好 Hash。槽 {@code {appKey}}。 */
    public static String buildTranslateUserPrefCacheKey(String appKey, String identity) {
        return buildBaseCacheKey(appKey) + "translate:user:" + withHashTag(stripHashTagChars(identity));
    }

    /** 坐席翻译偏好 Hash。槽 {@code {appKey}}。 */
    public static String buildTranslateAgentPrefCacheKey(String appKey, String agentId) {
        return buildBaseCacheKey(appKey) + "cs:agent:translate:" + withHashTag(stripHashTagChars(agentId));
    }

    /** 译文内容哈希。首 tag 为内容摘要，与锁同槽。 */
    public static String buildTranslateContentCacheKey(String appKey, String sourceLang, String targetLang, String sha256) {
        return buildCsTicketKeyPrefix(appKey) + "translate:content:" + withHashTag(stripHashTagChars(sha256))
                + COLON + sourceLang + COLON + targetLang;
    }

    /** 同一句译文 singleflight 锁，与内容缓存同槽。 */
    public static String buildTranslateLockCacheKey(String appKey, String sourceLang, String targetLang, String sha256) {
        return buildCsTicketKeyPrefix(appKey) + "translate:lock:" + withHashTag(stripHashTagChars(sha256))
                + COLON + sourceLang + COLON + targetLang;
    }

    /** 译文通知去重。槽 {@code {appKey}}。 */
    public static String buildTranslateNotifyOnceCacheKey(String appKey, String packetId, String language, String to) {
        return buildBaseCacheKey(appKey) + "translate:notify:" + withHashTag(stripHashTagChars(packetId))
                + COLON + stripHashTagChars(language) + COLON + stripHashTagChars(to);
    }

    /** 客服访客入站自动预译开关，值为 1/0。槽 {@code {appKey}}。 */
    public static String buildTranslateVisitorAutoInCacheKey(String appKey) {
        return buildBaseCacheKey(appKey) + "translate:cs:visitor-auto-in";
    }

    /** 访客翻译限流（咨询单）。槽 {@code {appKey}}。 */
    public static String buildTranslateGuestTicketLimitCacheKey(String appKey, long ticketId, long epochMinute) {
        return buildBaseCacheKey(appKey) + "translate:limit:ticket:" + ticketId + COLON + epochMinute;
    }

    /** 访客翻译限流（IP）。槽 {@code {appKey}}。 */
    public static String buildTranslateGuestIpLimitCacheKey(String appKey, String clientIp, long epochMinute) {
        return buildBaseCacheKey(appKey) + "translate:limit:ip:" + sanitizeTranslateIp(clientIp) + COLON + epochMinute;
    }

    /** 翻译语种目录快照，全平台一份。 */
    public static final String TRANSLATE_LANGUAGE_CATALOG_KEY = OUYUNC + "translate:languages";

    /** 语种目录变更通知频道。 */
    public static final String TRANSLATE_LANGUAGE_CATALOG_CHANNEL = OUYUNC + "translate:languages:channel";

    private static String sanitizeTranslateIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }
        String ip = stripHashTagChars(clientIp.trim()).replace(':', '_');
        return ip.length() > 64 ? ip.substring(0, 64) : ip;
    }

    /**
     * 用户设备单聊未读 Hash：槽 {@code {appKey:userId}}，与 session-read/unread-ids 同槽
     */
    public static String buildUserDeviceUnreadCacheKey(String appKey, String userId, Byte deviceType) {
        return buildAggregateCacheKey(appKey, userId) + USER_DEVICE_UNREAD + deviceType;
    }

    /**
     * 单聊未读 packetId ZSET（19 位 member）：与 unread/session-read 同属收件人槽
     */
    public static String buildUserDeviceUnreadIdsCacheKey(String appKey, String userId, Byte deviceType, String peerId) {
        return buildAggregateCacheKey(appKey, userId) + USER_DEVICE_UNREAD_IDS + deviceType
                + COLON + stripHashTagChars(peerId);
    }

    /**
     * 群关系版本：与 group-members/group-shield 同 {@code {appKey:groupId}} 槽
     */
    public static String buildGroupRelationVersionCacheKey(String appKey, String groupId) {
        return buildAggregateCacheKey(appKey, groupId) + GROUP_RELATION_VERSION;
    }

    /**
     * HTTP 外部推送幂等：按 messageId 分片
     */
    public static String buildHttpPushIdempotentCacheKey(String appKey, String messageId) {
        return buildAggregateCacheKey(appKey, messageId) + HTTP_PUSH_IDEM;
    }

    private static final String CS_SESSION_ROUTE = "cs:session:route:";

    /** 客服咨询单（ticket）维度最后一条聊天消息 packetId */
    private static final String CS_TICKET = "cs:ticket:";

    /**
     * 客服 ticket 维 key 前缀：appKey 只做命名空间，<strong>不加 hash tag</strong>。
     * <p>Cluster 唯一 tag 是后续的 {@code {ticketId}}，避免大租户 route/msgs/session-read/unread/last-msg 全部打到 {@code {appKey}} 单槽。
     * 同一 ticket 的 Lua（未读+已读等）KEYS 仍同槽。</p>
     */
    private static String buildCsTicketKeyPrefix(String appKey) {
        return OUYUNC + APP_KEY + sanitizeAppKeyToken(appKey) + COLON;
    }

    private static String sanitizeAppKeyToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return MessageConstant.DEFAULT_APP_KEY;
        }
        return stripHashTagChars(raw.trim());
    }

    /** 去掉花括号，避免片段本身变成 Cluster hash tag。 */
    private static String stripHashTagChars(String raw) {
        return raw.replace('{', '_').replace('}', '_');
    }

    /**
     * 客服会话路由（主键 = ticketId）：Hash 含 sessionId / serviceIdentity / assigneeId / agentType / channel。
     * {@code ouyunc:app:appKey:cs:session:route:{ticketId}}
     */
    public static String buildCsSessionRouteCacheKey(String appKey, String ticketId) {
        return buildCsTicketKeyPrefix(appKey) + CS_SESSION_ROUTE + withHashTag(stripHashTagChars(ticketId.trim()));
    }

    /**
     * 客服咨询单最后一条聊天消息：值为 {@link com.ouyunc.base.packet.Packet#getPacketId()}（Long）。
     * <p>SLA 扫描应读本 key；写入使用 Lua max-merge 保证并发安全。</p>
     */
    public static String buildCsTicketLastMessageCacheKey(String appKey, String ticketId) {
        return buildCsTicketKeyPrefix(appKey) + CS_TICKET + withHashTag(stripHashTagChars(ticketId.trim())) + COLON + LAST_MESSAGE;
    }

    /** 客服咨询单消息 ZSet 索引（ticket 维度，与 channel sessionId 分离）。 */
    private static final String MSGS = "msgs";

    /**
     * 客服咨询单消息会话 ZSet：member=packetId，score=0。
     */
    public static String buildCsTicketMessageSessionCacheKey(String appKey, String ticketId) {
        return buildCsTicketKeyPrefix(appKey) + CS_TICKET + withHashTag(stripHashTagChars(ticketId.trim())) + COLON + MSGS;
    }

    /** ticket 维度已读 offset Hash：field={@code readerId:deviceType}，value=max packetId。 */
    private static final String CS_TICKET_SRO = "session-read";

    public static String buildCsTicketReadOffsetHashCacheKey(String appKey, String ticketId) {
        return buildCsTicketKeyPrefix(appKey) + CS_TICKET + withHashTag(stripHashTagChars(ticketId.trim())) + COLON + CS_TICKET_SRO;
    }

    /** ticket 维度未读 Hash：field={@code readerId:deviceType}，value=未读计数。 */
    private static final String CS_TICKET_UR = "unread";

    /** ticket 维度未读 packetId 集合后缀（按 readerDeviceField 分 key）。 */
    private static final String CS_TICKET_UR_IDS = "unread-ids";

    public static String buildCsTicketUnreadHashCacheKey(String appKey, String ticketId) {
        return buildCsTicketKeyPrefix(appKey) + CS_TICKET + withHashTag(stripHashTagChars(ticketId.trim())) + COLON + CS_TICKET_UR;
    }

    /**
     * ticket 未读 packetId 集合：与 unread/session-read Hash 同 ticket 槽，支持按 offset 部分清除。
     */
    public static String buildCsTicketUnreadIdsCacheKey(String appKey, String ticketId, String readerDeviceField) {
        return buildCsTicketKeyPrefix(appKey) + CS_TICKET + withHashTag(stripHashTagChars(ticketId.trim()))
                + COLON + CS_TICKET_UR_IDS + COLON + readerDeviceField;
    }

    /** ticket 已读/未读 Hash field：{@code readerId + ":" + deviceType}。 */
    public static String buildCsTicketReaderDeviceField(String readerId, byte deviceType) {
        return readerId + COLON + deviceType;
    }

    // ---------- 内容安全（敏感词 / 策略 / 热更新）----------

    /** 平台默认词库租户标记，加载时与租户词库合并。 */
    public static final String CONTENT_SAFETY_GLOBAL_APP_KEY = "__global__";

    /**
     * 租户策略 Redis String key，值为 ContentSafetyPolicy JSON。
     *
     * @param appKey 租户
     * @return Redis key
     */
    public static String buildContentSafetyPolicyCacheKey(String appKey) {
        return OUYUNC + "im:cs:policy:" + appKey;
    }

    /**
     * 词库 Redis Hash：field=word，value=category|level。
     *
     * @param appKey 租户或 {@link #CONTENT_SAFETY_GLOBAL_APP_KEY}
     * @return Redis key
     */
    public static String buildContentSafetyWordsCacheKey(String appKey) {
        return OUYUNC + "im:cs:words:" + appKey;
    }

    /**
     * 词库版本号 Redis String，变更时 INCR。
     *
     * @param appKey 租户
     * @return Redis key
     */
    public static String buildContentSafetyVersionCacheKey(String appKey) {
        return OUYUNC + "im:cs:version:" + appKey;
    }

    /** Pub/Sub 频道名；payload 为 appKey 或 {@link #CONTENT_SAFETY_RELOAD_ALL}。 */
    public static final String CONTENT_SAFETY_RELOAD_CHANNEL = OUYUNC + "im:cs:reload";

    /** 内容安全热更新：全部租户失效。 */
    public static final String CONTENT_SAFETY_RELOAD_ALL = "ALL";

    /**
     * 关系本机缓存失效 Pub/Sub 频道；payload 为 {@code RelationCacheInvalidateEvent} JSON。
     * <p>IM 与 micro-cloud 须共用同一 Redis 与本频道名。</p>
     */
    public static final String RELATION_CACHE_INVALIDATE_CHANNEL = OUYUNC + "im:relation-cache:invalidate";
}
