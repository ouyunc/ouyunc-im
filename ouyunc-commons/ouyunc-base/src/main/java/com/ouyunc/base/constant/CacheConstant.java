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
    private static final String APP_KEY = "ak:";

    /***
     * appKey 下的identity 的 客户端信息
     */
    private static final String CLIENT_INFO = "ci:";

    /***
     * 消息缓存公共前缀
     */
    private static final String MESSAGE = "msg:";

    /***
     * 会话已读消息偏移量缓存公共前缀
     */
    private static final String SESSION_READ_MESSAGE_OFFSET = "sro:";

    /***
     * 锁
     */
    private static final String LOCK = "lock:";

    /***
     * 用户
     */
    private static final String USER = "u:";

    /***
     * 群组绑定的用户
     */
    private static final String GROUP_USERS = "gu:";

    /** 群成员关系版本（回源 CAS） */
    private static final String GROUP_RELATION_VERSION = "grv:";

    /***
     * 群成员的信息配置
     */
    private static final String GROUP_USERS_CONFIG = "guc:";

    /**
     * 群成员屏蔽索引 Hash（field=memberId）
     */
    private static final String GROUP_USERS_SHIELD = "gsh:";

    /**
     * MQTT retain / inflight
     */
    private static final String MQTT_RETAIN = "rt:";
    private static final String MQTT_RETAIN_TOPICS = "rts";
    private static final String MQTT_INFLIGHT = "if:";
    private static final String MQTT_MSG_ID = "mid:";
    private static final String MONGO_COMPENSATE = "im:mongo:cp:";

    /***
     * 好友列表
     */
    private static final String FRIENDS = "f:";

    /***
     * 配置， 我的好友信息的配置
     */
    private static final String FRIENDS_CONFIG = "fc:";

    /***
     * 用户-群列表
     */
    private static final String GROUPS = "g:";

    /***
     * 群
     */
    private static final String GROUP = "grp:";

    /***
     * 黑名单
     */
    private static final String BLACKLIST = "bl:";

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
    private static final String QOS_IDEM_CLI = "cli:";

    /***
     * 会话
     */
    private static final String SESSION = "s:";

    /***
     * 聊天会话
     */
    private static final String CHAT_SESSION = "cs:";

    /***
     * 好友请求
     */
    private static final String FRIEND_REQUEST = "fr:";

    /***
     * 正在处理中的好友请求会话标识
     */
    private static final String FRIEND_REQUEST_SESSION = "frs:";

    /***
     * 正在处理中的群请求会话标识
     */
    private static final String GROUP_REQUEST_SESSION = "grs:";

    /***
     * 群请求
     */
    private static final String GROUP_REQUEST = "gr:";

    /***
     * mqtt
     */
    private static final String MQTT = "mqtt:";

    /***
     * topic
     */
    private static final String TOPIC = "t:";

    /***
     * topic-list
     */
    private static final String TOPIC_LIST = "tl";

    /***
     * 设备 类型device-type
     */
    private static final String DEVICE_TYPE = "dt";

    /***
     * 最后一条消息
     */
    private static final String LAST_MESSAGE = "lm";

    /** 用户设备单聊未读 Hash 前缀（群聊不在此存储） */
    private static final String USER_DEVICE_UNREAD = "ur:";

    /** 用户设备单聊未读 packetId 集合前缀（有序清除用，member=packetId 十进制串） */
    private static final String USER_DEVICE_UNREAD_IDS = "urids:";

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
     * 租户级前缀（仅适合真正的 app 全局小集合：设备类型表、MQTT topic 列表、QoS 幂等等）。
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
     * 构建基础 appKey锁 缓存key - 集群优化
     */
    public static String buildAppKeyLockCacheKey(String appKey) {
        return OUYUNC + LOCK + APP_KEY + withHashTag(appKey);
    }

    /**
     * 构建appKey identity 关闭连接的分布式锁key - 集群优化
     */
    public static String buildIdentityBindOrUnbindLockCacheKey(String appKey, String comboIdentity) {
        String identity = IdentityUtil.revertIdentity(comboIdentity);
        return OUYUNC + LOCK + withHashTag(stripHashTagChars(identity)) + COLON + APP_KEY + appKey + COLON + comboIdentity;
    }

    /**
     * 构建appKey 下的好友请求/同意/拒绝的分布式锁key - 集群优化
     */
    public static String buildFriendRequestLockCacheKey(String appKey, String sessionId) {
        return buildAppKeyLockCacheKey(appKey) + COLON + FRIEND_REQUEST + withHashTag(sessionId);
    }

    /**
     * 构建appKey 下的群请求的分布式锁key - 集群优化
     */
    public static String buildGroupRequestLockCacheKey(String appKey, String joiner, String sessionId) {
        return buildAppKeyLockCacheKey(appKey) + COLON + GROUP_REQUEST + 
               withHashTag(joiner) + COLON + withHashTag(sessionId);
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
     * 会话已读偏移：槽跟收件人（from）收件箱一致，与 ur/urid Lua 同槽。
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
        return OUYUNC + "im:lg:" + withHashTag(identity) + COLON + appKey + COLON + deviceType;
    }

    /**
     * 身份路由 HASH：field=deviceType，value=nodeId|epoch。探测/多端在线看这把 key。
     */
    public static String buildLoginRouteCacheKey(String appKey, String identity) {
        return OUYUNC + "im:rt:" + withHashTag(identity) + COLON + appKey;
    }

    /**
     * IM 进程租约：value=epoch 字符串，PX 由心跳刷新。
     */
    public static String buildImNodeLeaseCacheKey(String nodeId) {
        return OUYUNC + "im:node:" + withHashTag(nodeId);
    }

    /**
     * 当前登记过的 IM 节点 id 集合（小 SET，心跳 SADD）。
     */
    public static String buildImNodeSetCacheKey() {
        return OUYUNC + "im:nodes";
    }

    /**
     * 节点连接数 HASH：field=appKey，value=count。与租约同 {@code {nodeId}} 槽，由心跳全量覆盖，不跟登录 Lua 同槽。
     */
    public static String buildImNodeConnHashCacheKey(String nodeId) {
        return OUYUNC + "im:cc:" + withHashTag(nodeId);
    }

    /**
     * 构建 user 用户 cache key - 集群优化
     */
    public static String buildUserCacheKey(String appKey, String identity) {
        return buildAggregateCacheKey(appKey, identity) + USER;
    }

    /**
     * 群成员 ZSET：槽 {@code {appKey:groupId}}，与 grv/shield 同槽。
     */
    public static String buildGroupUserCacheKey(String appKey, String groupId) {
        return buildAggregateCacheKey(appKey, groupId) + GROUP_USERS;
    }

    /**
     * 构建 群组成员在群中的配置信息 cache key - 集群优化
     */
    public static String buildGroupUserConfigCacheKey(String appKey, String memberId, String groupId) {
        return buildAggregateCacheKey(appKey, groupId) + GROUP_USERS_CONFIG + stripHashTagChars(memberId);
    }

    /**
     * 群屏蔽成员 Hash，与成员 ZSET / grv 同 {@code {appKey:groupId}} 槽。
     */
    public static String buildGroupShieldCacheKey(String appKey, String groupId) {
        return buildAggregateCacheKey(appKey, groupId) + GROUP_USERS_SHIELD;
    }

    public static String buildMqttRetainCacheKey(String appKey, String topic) {
        return buildAggregateCacheKey(appKey, topic) + MQTT + MQTT_RETAIN;
    }

    public static String buildMqttRetainTopicSetCacheKey(String appKey) {
        return buildBaseCacheKey(appKey) + MQTT + MQTT_RETAIN_TOPICS;
    }

    public static String buildMqttInflightCacheKey(String appKey, String comboIdentity) {
        return buildAggregateCacheKey(appKey, comboIdentity) + MQTT + MQTT_INFLIGHT;
    }

    public static String buildMqttMessageIdCacheKey(String appKey, String comboIdentity) {
        return buildAggregateCacheKey(appKey, comboIdentity) + MQTT + MQTT_MSG_ID;
    }

    /**
     * MySQL 已提交、Mongo 失败时的补偿 List，按 kind 分队列。
     */
    public static String buildMongoCompensateListCacheKey(String kind) {
        return OUYUNC + MONGO_COMPENSATE + kind;
    }

    /**
     * 构建 好友关系 cache key：按用户分片
     */
    public static String buildFriendsCacheKey(String appKey, String identity) {
        return buildAggregateCacheKey(appKey, identity) + FRIENDS;
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
     * QoS 幂等 packet：与 client key 同 Lua，保持租户级 {@code {appKey}} 同槽
     */
    public static String buildQosIdempotencyPacketKey(String appKey, long packetId) {
        return buildBaseCacheKey(appKey) + QOS_IDEM + QOS_IDEM_PKT + stripHashTagChars(String.valueOf(packetId));
    }

    /**
     * QoS 幂等 client：与 packet key 同 {@code {appKey}} 槽
     */
    public static String buildQosIdempotencyClientKey(String appKey, String loginIdentity, String clientMessageId) {
        return buildBaseCacheKey(appKey) + QOS_IDEM + QOS_IDEM_CLI
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
     * 用户设备单聊未读 Hash：槽 {@code {appKey:userId}}，与 sro/urid 同槽
     */
    public static String buildUserDeviceUnreadCacheKey(String appKey, String userId, Byte deviceType) {
        return buildAggregateCacheKey(appKey, userId) + USER_DEVICE_UNREAD + deviceType;
    }

    /**
     * 单聊未读 packetId 集合：与 ur/sro 同属收件人槽
     */
    public static String buildUserDeviceUnreadIdsCacheKey(String appKey, String userId, Byte deviceType, String peerId) {
        return buildAggregateCacheKey(appKey, userId) + USER_DEVICE_UNREAD_IDS + deviceType
                + COLON + stripHashTagChars(peerId);
    }

    /**
     * 群关系版本：与 gu/gsh 同 {@code {appKey:groupId}} 槽
     */
    public static String buildGroupRelationVersionCacheKey(String appKey, String groupId) {
        return buildAggregateCacheKey(appKey, groupId) + GROUP_RELATION_VERSION;
    }

    /**
     * mqtt topic filter：按 topic 分片
     */
    public static String buildMqttTopicFilterCacheKey(String appKey, String topicFilter) {
        return buildAggregateCacheKey(appKey, topicFilter) + MQTT + TOPIC;
    }

    /**
     * mqtt topic list：租户级小集合
     */
    public static String buildMqttTopicListCacheKey(String appKey) {
        return buildBaseCacheKey(appKey) + MQTT + TOPIC_LIST;
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
     * <p>Cluster 唯一 tag 是后续的 {@code {ticketId}}，避免大租户 route/msgs/sro/ur/lm 全部打到 {@code {appKey}} 单槽。
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
     * {@code ouyunc:ak:appKey:cs:session:route:{ticketId}}
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
    private static final String CS_TICKET_SRO = "sro";

    public static String buildCsTicketReadOffsetHashCacheKey(String appKey, String ticketId) {
        return buildCsTicketKeyPrefix(appKey) + CS_TICKET + withHashTag(stripHashTagChars(ticketId.trim())) + COLON + CS_TICKET_SRO;
    }

    /** ticket 维度未读 Hash：field={@code readerId:deviceType}，value=未读计数。 */
    private static final String CS_TICKET_UR = "ur";

    /** ticket 维度未读 packetId 集合后缀（按 readerDeviceField 分 key）。 */
    private static final String CS_TICKET_UR_IDS = "urids";

    public static String buildCsTicketUnreadHashCacheKey(String appKey, String ticketId) {
        return buildCsTicketKeyPrefix(appKey) + CS_TICKET + withHashTag(stripHashTagChars(ticketId.trim())) + COLON + CS_TICKET_UR;
    }

    /**
     * ticket 未读 packetId 集合：与 ur/sro Hash 同 ticket 槽，支持按 offset 部分清除。
     */
    public static String buildCsTicketUnreadIdsCacheKey(String appKey, String ticketId, String readerDeviceField) {
        return buildCsTicketKeyPrefix(appKey) + CS_TICKET + withHashTag(stripHashTagChars(ticketId.trim()))
                + COLON + CS_TICKET_UR_IDS + COLON + readerDeviceField;
    }

    /** ticket 已读/未读 Hash field：{@code readerId + ":" + deviceType}。 */
    public static String buildCsTicketReaderDeviceField(String readerId, byte deviceType) {
        return readerId + COLON + deviceType;
    }
}
