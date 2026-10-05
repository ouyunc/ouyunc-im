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
    private static final String APP_KEY = "app";

    /***
     * appKey 下的identity 的 客户端信息
     */
    private static final String CLIENT_INFO = "client";

    /***
     * 消息缓存公共前缀
     */
    private static final String MESSAGE = "msg";

    /**
     * 消息撤回单调标记，与 {@code msg} 同槽；只增不减。
     */
    private static final String MESSAGE_WITHDRAWN = "msg-withdrawn";

    /***
     * 会话已读消息偏移量缓存公共前缀
     */
    private static final String SESSION_READ_MESSAGE_OFFSET = "session-read";

    /***
     * 锁
     */
    private static final String LOCK = "lock";

    /** 身份路由 HASH */
    private static final String IM_ROUTE = "im:route";

    /** 进程租约 */
    private static final String IM_NODE = "im:node";

    /** 登记节点发现索引（ZSET，score 为到期时间） */
    private static final String IM_NODES = "im:nodes";

    /** 节点连接数 HASH */
    private static final String IM_CONN = "im:conn";

    /** appKey 连接配额 HASH，field=nodeId，value=count|lastSeenUnixSeconds */
    private static final String IM_QUOTA = "im:quota";

    /***
     * 用户
     */
    private static final String USER = "user";

    /***
     * 群组绑定的用户
     */
    private static final String GROUP_USERS = "group-members";

    /**
     * 群成员 ZSET 完整性标记，与 {@code group-members} 同槽；禁止把哨兵写进 ZSET member。
     * 值为成员数，须与 ZCARD 一致；不一致则删标记并回源。
     */
    private static final String GROUP_USERS_INIT = "group-members-init";

    /** 群成员关系版本（回源 CAS） */
    private static final String GROUP_RELATION_VERSION = "group-relation-ver";

    /***
     * 群成员的信息配置
     */
    private static final String GROUP_USERS_CONFIG = "group-member-cfg";

    /**
     * 群成员屏蔽索引 Hash（field=memberId）。完整性用旁边的 {@code group-shield-init} STRING，不往 Hash 里塞哨兵。
     */
    private static final String GROUP_USERS_SHIELD = "group-shield";

    /** 群屏蔽索引完整性标记，与 group-shield 同槽 */
    private static final String GROUP_USERS_SHIELD_INIT = "group-shield-init";

    /***
     * 好友列表
     */
    private static final String FRIENDS = "friends";

    /**
     * 好友 ZSET 与库一致的标记，与 {@code friends} 同槽；禁止把哨兵写进 ZSET member。
     * 值为名单基数（完整为正、截断为负），须与 ZCARD 一致。
     */
    private static final String FRIENDS_INIT = "friends-init";

    /** 好友关系版本（回源 CAS），与 friends/friends-init 同槽 */
    private static final String FRIENDS_RELATION_VERSION = "friends-relation-ver";

    /***
     * 配置， 我的好友信息的配置
     */
    private static final String FRIENDS_CONFIG = "friend-cfg";

    /***
     * 用户-群列表
     */
    private static final String GROUPS = "groups";

    /**
     * 用户已加入群 ZSET 完整性标记，与 {@code groups:} 同槽。
     */
    private static final String USER_GROUPS_INIT = "groups-init";

    /** 用户加群关系版本（回源 CAS），与 groups/groups-init 同槽 {@code {appKey:userId}} */
    private static final String USER_GROUPS_RELATION_VERSION = "groups-relation-ver";

    /***
     * 群
     */
    private static final String GROUP = "group";

    /***
     * 黑名单 Hash（field=被拉黑人）。完整性用旁边的 {@code blacklist-init} STRING。
     */
    private static final String BLACKLIST = "blacklist";

    /** 黑名单 Hash 完整性标记，与 blacklist 同槽 */
    private static final String BLACKLIST_INIT = "blacklist-init";

    /**
     * 关系名单回源临时 ZSET 后缀，必须接在已含 hash tag 的 zset key 后以同槽。
     */
    private static final String RELATION_ROSTER_TMP = ":tmp";

    /***
     * QoS 幂等
     */
    private static final String QOS_IDEM = "qos:idem";

    /***
     * http push 幂等
     */
    private static final String HTTP_PUSH_IDEM = "http-push:idem";

    /***
     * QoS 幂等 cli
     */
    private static final String QOS_IDEM_CLI = "client-msg";

    /***
     * 会话
     */
    private static final String SESSION = "session";

    /***
     * 聊天会话
     */
    private static final String CHAT_SESSION = "chat-session";

    /***
     * 好友请求
     */
    private static final String FRIEND_REQUEST = "friend-req";

    /***
     * 正在处理中的好友请求会话标识
     */
    private static final String FRIEND_REQUEST_SESSION = "friend-req-session";

    /**
     * 外渠任务 Hash：field={@code recipientId|channel}，值 P=待确认、C=已确认。
     * 身份是 appKey + canonical packetId + 收件人 + 渠道，避免同租户 messageId 互相清除。
     */
    private static final String EXTERNAL_DELIVERY_TASK = "ext-task";

    /** 首次扇出已完成。重复请求看到该键后不再广播。 */
    private static final String DELIVERY_DONE = "delivery-done";

    /** 首次扇出进行中的 owner 锁，崩溃后靠 TTL 释放。 */
    private static final String DELIVERY_RUN = "delivery-run";

    /***
     * 正在处理中的群请求会话标识
     */
    private static final String GROUP_REQUEST_SESSION = "group-req-session";

    /***
     * 群请求
     */
    private static final String GROUP_REQUEST = "group-req";

    /***
     * 设备 类型device-type
     */
    private static final String DEVICE_TYPE = "device-type";

    /**
     * 业务空闲下行文案 Hash：field={@code {loginScopeName}:{variant}}，如 {@code cs_agent:1}、{@code normal:2:pre-close}。
     */
    private static final String IDLE_NOTIFY = "idle-notify";

    /***
     * 最后一条消息
     */
    private static final String LAST_MESSAGE = "last-msg";

    /** 用户设备单聊未读 Hash 前缀（群聊不在此存储） */
    private static final String USER_DEVICE_UNREAD = "unread";

    /** 用户设备单聊未读 packetId 集合前缀（有序清除用，member=packetId 十进制串） */
    private static final String USER_DEVICE_UNREAD_IDS = "unread-ids";

    /** 翻译语种目录快照，全平台一份。 */
    public static final String TRANSLATE_LANGUAGE_CATALOG_KEY = OUYUNC + "translate:languages";

    /** 语种目录变更通知频道。 */
    public static final String TRANSLATE_LANGUAGE_CATALOG_CHANNEL = OUYUNC + "translate:languages:channel";

    /** 客服会话路由。 */
    private static final String CS_SESSION_ROUTE = "session-route";

    /** 客服咨询单（ticket）维度最后一条聊天消息 packetId。 */
    private static final String CS_TICKET = "cs:ticket";

    /** 客服咨询单消息 ZSet 索引（ticket 维度，与 channel sessionId 分离）。 */
    private static final String MSGS = "msgs";

    /** ticket 维度已读 offset Hash：field={@code readerId:deviceType}，value=max packetId。 */
    private static final String CS_TICKET_SRO = "session-read";

    /** ticket 维度未读 Hash：field={@code readerId:deviceType}，value=未读计数。 */
    private static final String CS_TICKET_UR = "unread";

    /** ticket 维度未读 packetId 集合后缀（按 readerDeviceField 分 key）。 */
    private static final String CS_TICKET_UR_IDS = "unread-ids";

    // ---------- 内容安全（敏感词 / 策略 / 热更新）----------

    /** 平台默认词库租户标记，加载时与租户词库合并。 */
    public static final String CONTENT_SAFETY_GLOBAL_APP_KEY = "__global__";

    /** Pub/Sub 频道名；payload 为 appKey 或 {@link #CONTENT_SAFETY_RELOAD_ALL}。 */
    public static final String CONTENT_SAFETY_RELOAD_CHANNEL = OUYUNC + "im:safety:reload";

    /** 内容安全热更新：全部租户失效。 */
    public static final String CONTENT_SAFETY_RELOAD_ALL = "ALL";

    /**
     * 业务空闲文案本机缓存失效频道；payload 为 appKey 或 {@link #CONTENT_SAFETY_RELOAD_ALL}。
     * IM 与 CS 须共用同一 Redis，发布端用 Redisson Topic。
     */
    public static final String IDLE_NOTIFY_RELOAD_CHANNEL = IdleNotifyConstant.RELOAD_CHANNEL;

    /**
     * 关系本机缓存失效 Pub/Sub 频道；payload 为 {@code RelationCacheInvalidateEvent} JSON。
     * <p>IM 与 micro-cloud 须共用同一 Redis 与本频道名。</p>
     */
    public static final String RELATION_CACHE_INVALIDATE_CHANNEL = OUYUNC + "im:relation-cache:invalidate";

    /**
     * 登录路由本机缓存失效频道。登录绑定或解绑完成后发布，所有 IM 节点清理对应 Caffeine 项。
     * Redisson Topic 只负责降低旧路由窗口；消息丢失时仍由短 TTL 最终收敛。
     */
    public static final String LOGIN_ROUTE_CACHE_INVALIDATE_CHANNEL = OUYUNC + "im:login-route-cache:invalidate";

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
     * QoS 幂等按登录 identity 分片；普通会话、收件箱和群数据使用各自聚合实体分片。
     * 普通会话/收件箱/群数据请用 {@link #buildAggregateCacheKey}。
     */
    private static String buildBaseCacheKey(String appKey) {
        return OUYUNC + APP_KEY + COLON + withHashTag(sanitizeAppKeyToken(appKey)) + COLON;
    }

    /**
     * 按聚合实体分片的业务 key 前缀：首 tag 为 {@code {appKey:aggregateId}}。
     */
    private static String buildAggregateCacheKey(String appKey, String aggregateId) {
        return OUYUNC + APP_KEY + COLON + withAggregateHashTag(appKey, aggregateId) + COLON;
    }

    // ============================================ 分布式锁 ============================================

    /**
     * 构建appKey identity 关闭连接的分布式锁key - 集群优化
     */
    public static String buildIdentityBindOrUnbindLockCacheKey(String appKey, String comboIdentity) {
        String identity = IdentityUtil.revertIdentity(comboIdentity);
        return OUYUNC + LOCK + COLON + withAggregateHashTag(appKey, identity) + COLON
                + stripHashTagChars(comboIdentity == null ? "" : comboIdentity);
    }

    /**
     * 好友请求锁（P2）：首 tag {@code {appKey:sessionId}}，按会话分片，避免租户单槽热点。
     */
    public static String buildFriendRequestLockCacheKey(String appKey, String sessionId) {
        return OUYUNC + LOCK + COLON + withAggregateHashTag(appKey, sessionId) + COLON + FRIEND_REQUEST;
    }

    /**
     * 群请求锁（P2）：首 tag {@code {appKey:sessionId}}；joiner 仅作后缀、不再套多余 hash tag。
     */
    public static String buildGroupRequestLockCacheKey(String appKey, String joiner, String sessionId) {
        return OUYUNC + LOCK + COLON + withAggregateHashTag(appKey, sessionId) + COLON + GROUP_REQUEST
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
     * 租户业务空闲提示文案 Hash。{@code ouyunc:app:{appKey}:idle-notify}
     * <p>field 约定：{@code {LoginScopeEnum.name}:{strike}} 或 {@code {name}:{strike}:pre-close|repeat}；
     * 通配 {@code *:{variant}}。未配 field 时再读 {@link #buildIdleNotifyTextGlobalCacheKey()}。</p>
     */
    public static String buildIdleNotifyTextCacheKey(String appKey) {
        return buildBaseCacheKey(appKey) + IDLE_NOTIFY;
    }

    /**
     * 全平台默认业务空闲文案 Hash，field 与租户 Hash 相同。
     */
    public static String buildIdleNotifyTextGlobalCacheKey() {
        return OUYUNC + "im" + COLON + IDLE_NOTIFY + COLON + CONTENT_SAFETY_GLOBAL_APP_KEY;
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
        return buildAggregateCacheKey(appKey, from) + SESSION_READ_MESSAGE_OFFSET + COLON + identityType + COLON
                + peer + COLON + deviceType;
    }

    /**
     * 身份路由 HASH：槽 {@code {appKey:identity}}，field=deviceType，value=nodeId|epoch|lastLoginTime。
     * 完整登录上下文只保存在最终落地节点的 Channel 属性中。
     */
    public static String buildLoginRouteCacheKey(String appKey, String identity) {
        return OUYUNC + IM_ROUTE + COLON + withAggregateHashTag(appKey, identity);
    }

    /**
     * IM 进程租约：value=epoch 字符串，PX 由心跳刷新。
     */
    public static String buildImNodeLeaseCacheKey(String nodeId) {
        return OUYUNC + IM_NODE + COLON + withHashTag(nodeId);
    }

    /**
     * 登记过的 IM 节点发现索引（ZSET）。member=nodeId，score=Redis 时间 + 租约 TTL。
     * 心跳 ZADD 刷新到期时间，并按 score 回收过期成员。节点是否存活仍以各自租约 key 为准。
     */
    public static String buildImNodeRegistryCacheKey() {
        return OUYUNC + IM_NODES;
    }

    /**
     * 节点连接数 HASH：field=appKey，value=count。与租约同 {@code {nodeId}} 槽，由心跳全量覆盖，不跟登录 Lua 同槽。
     */
    public static String buildImNodeConnHashCacheKey(String nodeId) {
        return OUYUNC + IM_CONN + COLON + withHashTag(nodeId);
    }

    /**
     * appKey 连接配额 HASH：field=nodeId，value=count|lastSeenUnixSeconds。
     * 标签 {@code {appKey}}，各节点在同一槽上原子求和预占。
     */
    public static String buildAppKeyConnQuotaHashCacheKey(String appKey) {
        return OUYUNC + IM_QUOTA + COLON + withHashTag(sanitizeAppKeyToken(appKey));
    }

    /**
     * 配额 HASH 扫描模式。本机连接为 0 时仍要扫到死节点残留 field。
     */
    public static String appKeyConnQuotaKeyPattern() {
        return OUYUNC + IM_QUOTA + COLON + "*";
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
     * 群成员配置 Hash：槽 {@code {appKey:groupId}}，field 为 memberId。
     * 与成员 ZSET 同槽，避免大群一人一条 STRING。
     */
    public static String buildGroupUserConfigCacheKey(String appKey, String groupId) {
        return buildAggregateCacheKey(appKey, groupId) + GROUP_USERS_CONFIG;
    }

    /** Hash field，去掉花括号以免被误当成槽。 */
    public static String groupUserConfigField(String memberId) {
        return stripHashTagChars(memberId == null ? "" : memberId);
    }

    /** 本机 Caffeine 键，不是 Redis key。 */
    public static String buildGroupUserConfigLocalCacheKey(String appKey, String groupId, String memberId) {
        return buildGroupUserConfigCacheKey(appKey, groupId) + COLON + groupUserConfigField(memberId);
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
     * 好友关系版本：与 friends/friends-init 同 {@code {appKey:identity}} 槽。
     */
    public static String buildFriendsRelationVersionCacheKey(String appKey, String identity) {
        return buildAggregateCacheKey(appKey, identity) + FRIENDS_RELATION_VERSION;
    }

    /**
     * 好友配置 STRING：槽跟主人 {@code {appKey:from}}，与好友名单同槽；后缀是对端。
     */
    public static String buildFriendsConfigCacheKey(String appKey, String from, String to) {
        return buildAggregateCacheKey(appKey, from) + FRIENDS_CONFIG + COLON + stripHashTagChars(to);
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
     * 用户加群关系版本：与 groups/groups-init 同 {@code {appKey:userId}} 槽。
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
     * QoS 幂等权威键：登录身份隔离客户端 messageId，首次正式 packetId 保存在记录值中。
     */
    public static String buildQosIdempotencyClientKey(String appKey, String loginIdentity, String clientMessageId) {
        return buildAggregateCacheKey(appKey, loginIdentity) + QOS_IDEM + COLON + QOS_IDEM_CLI + COLON
                + stripHashTagChars(clientMessageId);
    }

    /**
     * 会话：槽 {@code {appKey:sessionId}}
     */
    public static String buildSessionCacheKey(String appKey, String sessionId) {
        return buildAggregateCacheKey(appKey, sessionId) + SESSION;
    }

    /**
     * 好友请求会话：槽按业务 sessionId。
     */
    public static String buildFriendRequestSessionCacheKey(String appKey, String sessionId, String friendRequestSessionId) {
        return buildAggregateCacheKey(appKey, sessionId) + FRIEND_REQUEST_SESSION
                + COLON + stripHashTagChars(friendRequestSessionId);
    }

    /**
     * 外渠任务槽 {@code {appKey:packetId}}。同一正式 packet 的收件人进度放在一个 Hash。
     * 使用 canonical packetId，不使用客户端 messageId，避免同租户不同发送者串键。
     */
    public static String buildExternalDeliveryTaskKey(String appKey, long packetId) {
        if (appKey == null || appKey.isBlank() || packetId <= 0L) {
            return null;
        }
        return buildAggregateCacheKey(appKey, String.valueOf(packetId)) + EXTERNAL_DELIVERY_TASK;
    }

    public static String buildDeliveryDoneKey(String appKey, long packetId) {
        if (appKey == null || appKey.isBlank() || packetId <= 0L) {
            return null;
        }
        return buildAggregateCacheKey(appKey, String.valueOf(packetId)) + DELIVERY_DONE;
    }

    public static String buildDeliveryRunKey(String appKey, long packetId) {
        if (appKey == null || appKey.isBlank() || packetId <= 0L) {
            return null;
        }
        return buildAggregateCacheKey(appKey, String.valueOf(packetId)) + DELIVERY_RUN;
    }

    /** 已校验的控制操作快照，与正式 packetId 同槽；不会混入聊天会话索引。 */
    public static String buildMessageOperationKey(String appKey, long packetId) {
        return buildAggregateCacheKey(appKey, String.valueOf(packetId)) + MESSAGE_OPERATION;
    }

    private static final String MESSAGE_OPERATION = ":operation";
    private static final String REQUEST_COMMAND_CONFIRMED = ":request-command-confirmed";

    /** 请求领域命令已获 broker 确认；通知重试不再次发布已确认命令。 */
    public static String buildRequestCommandConfirmedKey(String appKey, long packetId) {
        return buildAggregateCacheKey(appKey, String.valueOf(packetId)) + REQUEST_COMMAND_CONFIRMED;
    }

    /** 任务字段：收件人与渠道，不含客户端 messageId。 */
    public static String externalDeliveryTaskField(String recipientId, String channelKey) {
        if (recipientId == null || recipientId.isBlank() || channelKey == null || channelKey.isBlank()) {
            return null;
        }
        return recipientId + "|" + channelKey;
    }

    /**
     * 好友请求实体：槽按 from_to，避免和主人好友名单抢同一条大 key。
     */
    public static String buildFriendRequestCacheKey(String appKey, String from, String to) {
        String pair = stripHashTagChars(from) + MessageConstant.UNDERLINE + stripHashTagChars(to);
        return buildAggregateCacheKey(appKey, pair) + FRIEND_REQUEST;
    }

    /**
     * 群请求会话：槽按 joiner
     */
    public static String buildGroupRequestSessionCacheKey(String appKey, String joiner, String groupRequestSessionId) {
        return buildAggregateCacheKey(appKey, joiner) + GROUP_REQUEST_SESSION
                + COLON + stripHashTagChars(groupRequestSessionId);
    }

    /**
     * 群请求：槽按 groupId
     */
    public static String buildGroupRequestCacheKey(String appKey, String joiner, String groupId) {
        return buildAggregateCacheKey(appKey, groupId) + GROUP_REQUEST
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
     * 会话读模型 Hash：{@code ouyunc:app:{appKey:userId}:session-view:deviceType}。
     * 与单聊未读同槽；field=对端 id。ticket 数据仍走 cs:ticket 前缀。
     */
    public static String buildSessionViewCacheKey(String appKey, String userId, Byte deviceType) {
        return buildAggregateCacheKey(appKey, userId) + "session-view" + COLON + deviceType;
    }

    /**
     * IM 用户翻译偏好 Hash。槽 {@code {appKey:identity}}。
     * field 约定（业务侧写入，通信进程不写）：{@code language}、{@code autoTranslateIn}。
     */
    public static String buildTranslateUserPrefCacheKey(String appKey, String identity) {
        return buildAggregateCacheKey(appKey, identity) + "translate:user";
    }

    /**
     * 坐席翻译偏好 Hash。槽 {@code {appKey:agentId}}。
     * field 约定同用户偏好：{@code language}、{@code autoTranslateIn}。
     */
    public static String buildTranslateAgentPrefCacheKey(String appKey, String agentId) {
        return buildAggregateCacheKey(appKey, agentId) + "cs:agent:translate";
    }

    /** 译文内容哈希。首 tag 为内容摘要，与锁同槽。 */
    public static String buildTranslateContentCacheKey(String appKey, String sourceLang, String targetLang, String sha256) {
        return buildAppNamespaceKeyPrefix(appKey) + "translate:content" + COLON + withHashTag(stripHashTagChars(sha256))
                + COLON + sourceLang + COLON + targetLang;
    }

    /** 同一句译文 singleflight 锁，与内容缓存同槽。 */
    public static String buildTranslateLockCacheKey(String appKey, String sourceLang, String targetLang, String sha256) {
        return buildAppNamespaceKeyPrefix(appKey) + "translate:lock" + COLON + withHashTag(stripHashTagChars(sha256))
                + COLON + sourceLang + COLON + targetLang;
    }

    /** 译文通知去重。槽 {@code {appKey:packetId}}。 */
    public static String buildTranslateNotifyOnceCacheKey(String appKey, String packetId, String language, String to) {
        return buildAggregateCacheKey(appKey, packetId) + "translate:notify" + COLON
                + stripHashTagChars(language) + COLON + stripHashTagChars(to);
    }

    /** 客服访客入站自动预译开关，值为 1/0。槽 {@code {appKey}}。 */
    public static String buildTranslateVisitorAutoInCacheKey(String appKey) {
        return buildBaseCacheKey(appKey) + "translate:cs:visitor-auto-in";
    }

    /** 访客翻译限流（咨询单）。槽 {@code {appKey:ticketId}}，与 ticket 聚合同槽。 */
    public static String buildTranslateGuestTicketLimitCacheKey(String appKey, long ticketId, long epochMinute) {
        return buildAggregateCacheKey(appKey, String.valueOf(ticketId)) + "translate:limit:ticket" + COLON + epochMinute;
    }

    /** 访客翻译限流（IP）。槽 {@code {appKey:ip}}。 */
    public static String buildTranslateGuestIpLimitCacheKey(String appKey, String clientIp, long epochMinute) {
        return buildAggregateCacheKey(appKey, sanitizeTranslateIp(clientIp)) + "translate:limit:ip" + COLON + epochMinute;
    }

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
        return buildAggregateCacheKey(appKey, userId) + USER_DEVICE_UNREAD + COLON + deviceType;
    }

    /**
     * 单聊未读 packetId ZSET（19 位 member）：与 unread/session-read 同属收件人槽
     */
    public static String buildUserDeviceUnreadIdsCacheKey(String appKey, String userId, Byte deviceType, String peerId) {
        return buildAggregateCacheKey(appKey, userId) + USER_DEVICE_UNREAD_IDS + COLON + deviceType
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

    /**
     * 客服非 ticket 聚合 key 前缀。
     * <p>仅供翻译内容等自带 hash tag 的 key 使用，ticket 数据必须通过
     * {@link #buildCsTicketAggregateKeyPrefix(String, String)} 构造，禁止再次使用裸 {@code ticketId} hash tag。</p>
     */
    private static String buildAppNamespaceKeyPrefix(String appKey) {
        return OUYUNC + APP_KEY + COLON + sanitizeAppKeyToken(appKey) + COLON;
    }

    /**
     * 客服 ticket 聚合前缀：统一使用 {@code {appKey:ticketId}} 作为 Redis Cluster hash tag。
     * <p>同一租户、同一 ticket 的路由、消息索引、最后消息、已读和未读必须位于同一个 slot；
     * appKey 纳入 tag 后，不同租户的相同 ticketId 不再被固定分配到同一 slot。</p>
     */
    private static String buildCsTicketAggregateKeyPrefix(String appKey, String ticketId) {
        return buildAggregateCacheKey(appKey, ticketId) + CS_TICKET + COLON;
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
     * {@code ouyunc:app:{appKey:ticketId}:cs:ticket:session-route}
     */
    public static String buildCsSessionRouteCacheKey(String appKey, String ticketId) {
        return buildCsTicketAggregateKeyPrefix(appKey, ticketId) + CS_SESSION_ROUTE;
    }

    /**
     * 客服咨询单最后一条聊天消息：值为 {@link com.ouyunc.base.packet.Packet#getPacketId()}（Long）。
     * <p>SLA 扫描应读本 key；写入使用 Lua max-merge 保证并发安全。</p>
     */
    public static String buildCsTicketLastMessageCacheKey(String appKey, String ticketId) {
        return buildCsTicketAggregateKeyPrefix(appKey, ticketId) + LAST_MESSAGE;
    }

    /**
     * 客服咨询单消息会话 ZSet：member=packetId，score=0。
     */
    public static String buildCsTicketMessageSessionCacheKey(String appKey, String ticketId) {
        return buildCsTicketAggregateKeyPrefix(appKey, ticketId) + MSGS;
    }

    public static String buildCsTicketReadOffsetHashCacheKey(String appKey, String ticketId) {
        return buildCsTicketAggregateKeyPrefix(appKey, ticketId) + CS_TICKET_SRO;
    }

    public static String buildCsTicketUnreadHashCacheKey(String appKey, String ticketId) {
        return buildCsTicketAggregateKeyPrefix(appKey, ticketId) + CS_TICKET_UR;
    }

    /**
     * ticket 未读 packetId 集合：与 unread/session-read Hash 同 ticket 槽，支持按 offset 部分清除。
     */
    public static String buildCsTicketUnreadIdsCacheKey(String appKey, String ticketId, String readerDeviceField) {
        return buildCsTicketAggregateKeyPrefix(appKey, ticketId) + CS_TICKET_UR_IDS + COLON + readerDeviceField;
    }

    /** ticket 已读/未读 Hash field：{@code readerId + ":" + deviceType}。 */
    public static String buildCsTicketReaderDeviceField(String readerId, byte deviceType) {
        return readerId + COLON + deviceType;
    }

    /**
     * 租户策略 Redis String key，值为 ContentSafetyPolicy JSON。
     *
     * @param appKey 租户
     * @return Redis key
     */
    public static String buildContentSafetyPolicyCacheKey(String appKey) {
        return OUYUNC + "im:safety:policy" + COLON + appKey;
    }

    /**
     * 词库 Redis Hash：field=word，value=category|level。
     *
     * @param appKey 租户或 {@link #CONTENT_SAFETY_GLOBAL_APP_KEY}
     * @return Redis key
     */
    public static String buildContentSafetyWordsCacheKey(String appKey) {
        return OUYUNC + "im:safety:words" + COLON + appKey;
    }

    /**
     * 词库版本号 Redis String，变更时 INCR。
     *
     * @param appKey 租户
     * @return Redis key
     */
    public static String buildContentSafetyVersionCacheKey(String appKey) {
        return OUYUNC + "im:safety:version" + COLON + appKey;
    }

}
