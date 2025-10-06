package com.ouyunc.base.constant;

/**
 * @Author fzx
 * @Description: 缓存相关常量类
 **/
public class CacheConstant {

    /***
     * 冒号
     */
    private static final String COLON  = ":";

    /***
     * ouyunc 公共前缀
     */
    private static final String OUYUNC = MessageConstant.OUYUNC + COLON;

    /***
     * 所有的appKey
     */
    private static final String APP_KEYS = "app-keys";

    /***
     * 平台的 唯一标识key 公共前缀
     */
    private static final String APP_KEY = "app-key:";

    /***
     * appKey 下的identity 的 客户端信息
     */
    private static final String CLIENT_INFO = "client-info:";

    /***
     * 消息缓存公共前缀
     */
    private static final String MESSAGE = "message:";

    /***
     * 会话已读消息偏移量缓存公共前缀
     */
    private static final String SESSION_READ_MESSAGE_OFFSET = "session-read-message-offset:";

    /***
     * 平台appKey链接数
     */
    private static final String CONNECTIONS = "connections";

    /***
     * 锁
     */
    private static final String LOCK = "lock:";

    /***
     * 登录
     */
    private static final String LOGIN = "login:";

    /***
     * 用户
     */
    private static final String USER = "user:";

    /***
     * 群组绑定的用户
     */
    private static final String GROUP_USERS = "group-users:";

    /***
     * 群成员的信息配置
     */
    private static final String GROUP_USERS_CONFIG = "group-users-config:";


    /***
     * 好友列表
     */
    private static final String FRIENDS = "friends:";


    /***
     * 配置， 我的好友信息的配置
     */
    private static final String FRIENDS_CONFIG = "friends-config:";


    /***
     * 用户-群列表
     */
    private static final String GROUPS = "groups:";




    /***
     * 群
     */
    private static final String GROUP = "group:";



    /***
     * 黑名单
     */
    private static final String BLACKLIST = "blacklist:";


    /***
     * 离线
     */
    private static final String OFFLINE = "offline:";

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
    private static final String FRIEND_REQUEST = "friend-request:";

    /***
     * 正在处理中的好友请求会话标识
     */
    private static final String FRIEND_REQUEST_SESSION = "friend-request-session:";

    /***
     * 正在处理中的群请求会话标识
     */
    private static final String GROUP_REQUEST_SESSION = "group-request-session:";

    /***
     * 群请求
     */
    private static final String GROUP_REQUEST = "groups-request:";


    /***
     * mqtt
     */
    private static final String MQTT = "mqtt:";

    /***
     * topic
     */
    private static final String TOPIC = "topic:";

    /***
     * topic-list
     */
    private static final String TOPIC_LIST = "topic-list";


    /***
     * 设备 类型device-type
     */
    private static final String DEVICE_TYPE = "device-type";

    /***
     * 最后一条消息
     */
    private static final String LAST_MESSAGE = "last-message";


    //==============================================对外缓存方法==================================================

    /**
     * 构建基础 缓存key
     */
    private static String buildBaseCacheKey(String appKey) {
        return OUYUNC + APP_KEY + appKey + COLON;
    }


    // ============================================分布式锁==================================================
    /**
     * 构建基础 appKey锁 缓存key
     */
    public static String buildAppKeyLockCacheKey(String appKey) {
        return OUYUNC + LOCK + APP_KEY + appKey;
    }

    /**
     * 构建appKey identity 关闭连接的分布式锁key
     */
    public static String buildIdentityBindOrUnbindLockCacheKey(String appKey, String comboIdentity) {
        return buildAppKeyLockCacheKey(appKey)  + COLON + comboIdentity;
    }

    /**
     * 构建appKey 下的好友请求/同意/拒绝的分布式锁key
     */
    public static String buildFriendRequestLockCacheKey(String appKey, String sessionId) {
        return buildAppKeyLockCacheKey(appKey)  + COLON + FRIEND_REQUEST + sessionId;
    }

    /**
     * 构建appKey 下的群请求的分布式锁key
     */
    public static String buildGroupRequestLockCacheKey(String appKey, String joiner, String sessionId) {
        return buildAppKeyLockCacheKey(appKey) + COLON + GROUP_REQUEST + joiner + COLON + sessionId;
    }




    /**
     * 构建appKey 下所有设备类型 缓存key
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
     * 构建appKey 下的identity 的远端客户端设置信息
     */
    public static String buildRemoteClientInfoCacheKey(String appKey, String identity) {
        return buildBaseCacheKey(appKey) + CLIENT_INFO + identity;
    }

    /**
     * 构建 本地客户端信息设置 cache key
     */
    public static String buildLocalClientInfoCacheKey(String appKey, String identity) {
        return appKey + COLON + identity;
    }

    /**
     * 构建 消息message  cache Key
     */
    public static String buildMessageCacheKey(String appKey, Long packetId) {
        return buildBaseCacheKey(appKey) + MESSAGE + packetId;
    }


    /**
     * 构建 会话中已读消息偏移量 cache key
     */
    public static String buildSessionReadMessageOffsetCacheKey(String appKey, Integer identityType, String from,  Byte deviceType, String to) {
        return buildBaseCacheKey(appKey) + SESSION_READ_MESSAGE_OFFSET + identityType + COLON + from + COLON  + deviceType + COLON + to;
    }

    /**
     * 构建 平台appKey链接数 cache key
     */
    public static String buildConnectionsCacheKey(String appKey) {
        return buildBaseCacheKey(appKey) + CONNECTIONS;
    }

    /**
     * 构建 appKey 登录 cache key
     */
    public static String buildLoginCacheKey(String appKey, String comboIdentity) {
        return buildBaseCacheKey(appKey) + LOGIN + USER + comboIdentity;
    }

    /**
     * 构建 user 用户 cache key
     */
    public static String buildUserCacheKey(String appKey, String identity) {
        return buildBaseCacheKey(appKey) + USER + identity;
    }

    /**
     * 构建 群组成员 cache key
     */
    public static String buildGroupUserCacheKey(String appKey, String groupId) {
        return buildBaseCacheKey(appKey) + GROUP_USERS + groupId;
    }

    /**
     * 构建 群组成员在群中的配置信息 cache key
     */
    public static String buildGroupUserConfigCacheKey(String appKey, String memberId, String groupId) {
        return buildBaseCacheKey(appKey) + GROUP_USERS_CONFIG + memberId + COLON + groupId;
    }

    /**
     * 构建 好友关系 cache key
     */
    public static String buildFriendsCacheKey(String appKey, String identity) {
        return buildBaseCacheKey(appKey) + FRIENDS + identity;
    }

    /**
     * 构建 好友关系配置信息 cache key
     */
    public static String buildFriendsConfigCacheKey(String appKey, String from, String to) {
        return buildBaseCacheKey(appKey) + FRIENDS_CONFIG + from + COLON + to;
    }

    /**
     * 构建 用户所加入的群组 cache key
     */
    public static String buildUserGroupsCacheKey(String appKey, String userId) {
        return buildBaseCacheKey(appKey) + GROUPS + userId;
    }

    /**
     * 构建 群组信息 cache key
     */
    public static String buildGroupCacheKey(String appKey, String groupId) {
        return buildBaseCacheKey(appKey) + GROUP + groupId;
    }

    /**
     * 构建 identity 的黑名单 cache key
     */
    public static String buildBlacklistCacheKey(String appKey, String identity) {
        return buildBaseCacheKey(appKey) + BLACKLIST + identity;
    }

    /**
     * 构建 离线消息 cache key
     */
    public static String buildOfflineCacheKey(String appKey, String identity, String deviceTypeName) {
        return buildBaseCacheKey(appKey) + OFFLINE + identity + COLON + deviceTypeName;
    }

    /**
     * 构建 会话session cache key
     */
    public static String buildSessionCacheKey(String appKey, String sessionId) {
        return buildBaseCacheKey(appKey) + SESSION + sessionId;
    }

    /**
     * 构建 好友请求会话session cache key
     */
    public static String buildFriendRequestSessionCacheKey(String appKey, String sessionId, String friendRequestSessionId) {
        return buildBaseCacheKey(appKey) + FRIEND_REQUEST + SESSION + sessionId + COLON + friendRequestSessionId;
    }

    /**
     * 构建 好友请求 cache key
     */
    public static String buildFriendRequestCacheKey(String appKey, String from, String to) {
        return buildBaseCacheKey(appKey) + FRIEND_REQUEST_SESSION + from + COLON + to;
    }

    /**
     * 构建 群组请求会话session cache key
     */
    public static String buildGroupRequestSessionCacheKey(String appKey, String joiner, String groupRequestSessionId) {
        return buildBaseCacheKey(appKey) + GROUP_REQUEST + SESSION + joiner + COLON + groupRequestSessionId;
    }

    /**
     * 构建 群组请求 cache key
     */
    public static String buildGroupRequestCacheKey(String appKey, String joiner, String groupId) {
        return buildBaseCacheKey(appKey) + GROUP_REQUEST_SESSION + joiner + COLON + groupId;
    }
    /**
     * 构建 会话最后一条信息 cache key
     */
    public static String buildSessionLastMessageCacheKey(String appKey, String sessionId) {
        return buildBaseCacheKey(appKey) + SESSION + sessionId + COLON + LAST_MESSAGE;
    }
    /**
     * 构建 聊天消息会话 cache key
     */
    public static String buildChatSessionCacheKey(String appKey, String identity, Byte deviceType) {
        return buildBaseCacheKey(appKey) + CHAT_SESSION + identity + COLON + deviceType;
    }
    /**
     * 构建 mqtt topic  cache key
     */
    public static String buildMqttTopicFilterCacheKey(String appKey, String topicFilter) {
        return buildBaseCacheKey(appKey) + MQTT + TOPIC + topicFilter;
    }
    /**
     * 构建 mqtt topic list cache key
     */
    public static String buildMqttTopicListCacheKey(String appKey) {
        return buildBaseCacheKey(appKey) + MQTT + TOPIC_LIST;
    }





}
