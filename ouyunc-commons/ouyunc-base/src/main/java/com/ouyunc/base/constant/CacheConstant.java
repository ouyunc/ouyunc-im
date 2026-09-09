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

    /***
     * 群成员的信息配置
     */
    private static final String GROUP_USERS_CONFIG = "guc:";

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

    // ============================================ 集群优化方法 ============================================

    /**
     * 为集群环境构建哈希标签 - 确保相关数据在同一个slot
     */
    private static String withHashTag(String key) {
        return HASH_TAG_START + key + HASH_TAG_END;
    }

    /**
     * 构建基础 缓存key - 集群优化版
     * <p>Cluster 只认 key 中<strong>第一个</strong>{@code {...}} 为槽。本前缀已带 {@code {appKey}}，
     * 后续再包的 {@code {packetId}}/{@code {userId}} 等只是命名，不改槽。同一 app 的 MGET 因此同槽合法，
     * 但会打热点槽；登录/租约/ticket 等 key 故意不用本前缀，避免与 identity/ticket 槽冲突。</p>
     */
    private static String buildBaseCacheKey(String appKey) {
        return OUYUNC + APP_KEY + withHashTag(appKey) + COLON;
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
        return buildBaseCacheKey(appKey) + CLIENT_INFO + withHashTag(identity);
    }

    /**
     * 构建 本地客户端信息设置 cache key - 集群优化
     */
    public static String buildLocalClientInfoCacheKey(String appKey, String identity) {
        return appKey + COLON + withHashTag(identity);
    }

    /**
     * 构建 消息message cache Key - 集群优化
     */
    public static String buildMessageCacheKey(String appKey, Long packetId) {
        // 第一个 tag 仍是 {appKey}；{packetId} 仅作可读片段。跨 packet MGET 同 app 同槽。
        return buildBaseCacheKey(appKey) + MESSAGE + withHashTag(String.valueOf(packetId));
    }

    /**
     * 构建 会话中已读消息偏移量 cache key - 集群优化
     */
    public static String buildSessionReadMessageOffsetCacheKey(String appKey, Integer identityType,
                                                             String from, Byte deviceType, String to) {
        // 第一个 tag 仍是 {appKey}，与 ur Hash 同槽，供未读 Lua 双 KEYS 合法。
        String sessionTag = withHashTag(from + MessageConstant.UNDERLINE + to);
        return buildBaseCacheKey(appKey) + SESSION_READ_MESSAGE_OFFSET + identityType + COLON +
               sessionTag + COLON + deviceType;
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
        return buildBaseCacheKey(appKey) + USER + withHashTag(identity);
    }

    /**
     * 构建 群组成员 cache key - 集群优化
     */
    public static String buildGroupUserCacheKey(String appKey, String groupId) {
        return buildBaseCacheKey(appKey) + GROUP_USERS + withHashTag(groupId);
    }

    /**
     * 构建 群组成员在群中的配置信息 cache key - 集群优化
     */
    public static String buildGroupUserConfigCacheKey(String appKey, String memberId, String groupId) {
        // 使用groupId作为哈希标签，确保同一群组的配置在同一个slot
        return buildBaseCacheKey(appKey) + GROUP_USERS_CONFIG + memberId + COLON + withHashTag(groupId);
    }

    /**
     * 构建 好友关系 cache key - 集群优化
     */
    public static String buildFriendsCacheKey(String appKey, String identity) {
        return buildBaseCacheKey(appKey) + FRIENDS + withHashTag(identity);
    }

    /**
     * 构建 好友关系配置信息 cache key - 集群优化
     */
    public static String buildFriendsConfigCacheKey(String appKey, String from, String to) {
        // 使用from和to的组合作为哈希标签，确保同一好友关系的数据在同一个slot
        String friendTag = withHashTag(from + MessageConstant.UNDERLINE + to);
        return buildBaseCacheKey(appKey) + FRIENDS_CONFIG + friendTag;
    }

    /**
     * 构建 用户所加入的群组 cache key - 集群优化
     */
    public static String buildUserGroupsCacheKey(String appKey, String userId) {
        return buildBaseCacheKey(appKey) + GROUPS + withHashTag(userId);
    }

    /**
     * 构建 群组信息 cache key - 集群优化
     */
    public static String buildGroupCacheKey(String appKey, String groupId) {
        return buildBaseCacheKey(appKey) + GROUP + withHashTag(groupId);
    }

    /**
     * 构建 identity 的黑名单 cache key - 集群优化
     */
    public static String buildBlacklistCacheKey(String appKey, String identity) {
        return buildBaseCacheKey(appKey) + BLACKLIST + withHashTag(identity);
    }

    /**
     * QoS 幂等：服务端 packetId
     */
    public static String buildQosIdempotencyPacketKey(String appKey, long packetId) {
        return buildBaseCacheKey(appKey) + QOS_IDEM + QOS_IDEM_PKT + withHashTag(String.valueOf(packetId));
    }

    /**
     * QoS 幂等：通道登录身份 + 客户端 messageId
     */
    public static String buildQosIdempotencyClientKey(String appKey, String loginIdentity, String clientMessageId) {
        return buildBaseCacheKey(appKey) + QOS_IDEM + QOS_IDEM_CLI + withHashTag(loginIdentity) + COLON + clientMessageId;
    }

    /**
     * 构建 会话session cache key - 集群优化
     */
    public static String buildSessionCacheKey(String appKey, String sessionId) {
        return buildBaseCacheKey(appKey) + SESSION + withHashTag(sessionId);
    }

    /**
     * 构建 好友请求会话session cache key - 集群优化
     */
    public static String buildFriendRequestSessionCacheKey(String appKey, String sessionId, String friendRequestSessionId) {
        return buildBaseCacheKey(appKey) + FRIEND_REQUEST + SESSION + 
               withHashTag(sessionId) + COLON + friendRequestSessionId;
    }

    /**
     * 构建 好友请求 cache key - 集群优化
     */
    public static String buildFriendRequestCacheKey(String appKey, String from, String to) {
        // 使用from和to的组合作为哈希标签
        String requestTag = withHashTag(from + MessageConstant.UNDERLINE + to);
        return buildBaseCacheKey(appKey) + FRIEND_REQUEST_SESSION + requestTag;
    }

    /**
     * 构建 群组请求会话session cache key - 集群优化
     */
    public static String buildGroupRequestSessionCacheKey(String appKey, String joiner, String groupRequestSessionId) {
        return buildBaseCacheKey(appKey) + GROUP_REQUEST + SESSION + 
               withHashTag(joiner) + COLON + groupRequestSessionId;
    }

    /**
     * 构建 群组请求 cache key - 集群优化
     */
    public static String buildGroupRequestCacheKey(String appKey, String joiner, String groupId) {
        // 使用joiner和groupId的组合作为哈希标签
        String requestTag = withHashTag(joiner + MessageConstant.UNDERLINE + groupId);
        return buildBaseCacheKey(appKey) + GROUP_REQUEST_SESSION + requestTag;
    }

    /**
     * 构建 会话最后一条信息 cache key - 集群优化
     */
    public static String buildSessionLastMessageCacheKey(String appKey, String sessionId) {
        return buildBaseCacheKey(appKey) + SESSION + withHashTag(sessionId) + COLON + LAST_MESSAGE;
    }

    /**
     * 构建 聊天消息会话 cache key - 集群优化
     */
    public static String buildChatSessionCacheKey(String appKey, String identity, Byte deviceType) {
        return buildBaseCacheKey(appKey) + CHAT_SESSION + withHashTag(identity) + COLON + deviceType;
    }

    /**
     * 用户在某设备上的单聊未读 Hash（群聊未读不在此 key 维护）。
     */
    public static String buildUserDeviceUnreadCacheKey(String appKey, String userId, Byte deviceType) {
        return buildBaseCacheKey(appKey) + USER_DEVICE_UNREAD + withHashTag(userId) + COLON + deviceType;
    }

    /**
     * 构建 mqtt topic cache key - 集群优化
     */
    public static String buildMqttTopicFilterCacheKey(String appKey, String topicFilter) {
        // 使用topicFilter作为哈希标签
        return buildBaseCacheKey(appKey) + MQTT + TOPIC + withHashTag(topicFilter);
    }

    /**
     * 构建 mqtt topic list cache key - 集群优化
     */
    public static String buildMqttTopicListCacheKey(String appKey) {
        return buildBaseCacheKey(appKey) + MQTT + TOPIC_LIST;
    }

    /**
     * HTTP 外部推送幂等键：im:{appKey}:http-push:idempotent:{messageId}
     */
    public static String buildHttpPushIdempotentCacheKey(String appKey, String messageId) {
        return buildBaseCacheKey(appKey) + HTTP_PUSH_IDEM + withHashTag(messageId);
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

    public static String buildCsTicketUnreadHashCacheKey(String appKey, String ticketId) {
        return buildCsTicketKeyPrefix(appKey) + CS_TICKET + withHashTag(stripHashTagChars(ticketId.trim())) + COLON + CS_TICKET_UR;
    }

    /** ticket 已读/未读 Hash field：{@code readerId + ":" + deviceType}。 */
    public static String buildCsTicketReaderDeviceField(String readerId, byte deviceType) {
        return readerId + COLON + deviceType;
    }
}
