package com.ouyunc.base.constant;

/**
 * @Author fzx
 * @Description: 缓存相关常量类
 **/
public class CacheConstant {

    /***
     * 冒号
     */
    public static final String COLON  = ":";

    /***
     * ouyunc 公共前缀
     */
    public static final String OUYUNC = MessageConstant.OUYUNC + COLON;

    /***
     * 所有的appKey
     */
    public static final String APP_KEYS = "app-keys";

    /***
     * 平台的 唯一标识key 公共前缀
     */
    public static final String APP_KEY = "app-key:";

    /***
     * appKey 下的identity 的 客户端信息
     */
    public static final String CLIENT_INFO = "client-info:";

    /***
     * 消息缓存公共前缀
     */
    public static final String MESSAGE = "message:";

    /***
     * 已读消息缓存公共前缀
     */
    public static final String READ_MESSAGE = "read-message:";

    /***
     * 会话已读消息偏移量缓存公共前缀
     */
    public static final String SESSION_READ_MESSAGE_OFFSET = "session-read-message-offset:";

    /***
     * 平台appKey链接数
     */
    public static final String CONNECTIONS = "connections";

    /***
     * 锁
     */
    public static final String LOCK = "lock:";

    /***
     * 登录
     */
    public static final String LOGIN = "login:";

    /***
     * 用户
     */
    public static final String USER = "user:";

    /***
     * 群组绑定的用户
     */
    public static final String GROUP_USERS = "group-users:";

    /***
     * 群成员的信息配置
     */
    public static final String GROUP_USERS_CONFIG = "group-users-config:";


    /***
     * 好友列表
     */
    public static final String FRIENDS = "friends:";


    /***
     * 配置， 我的好友信息的配置
     */
    public static final String FRIENDS_CONFIG = "friends-config:";


    /***
     * 用户-群列表
     */
    public static final String GROUPS = "groups:";




    /***
     * 群
     */
    public static final String GROUP = "group:";


    /***
     * 发送方
     */
    public static final String FROM = "from:";

    /***
     * 接收方
     */
    public static final String TO = "to:";


    /***
     * 黑名单
     */
    public static final String BLACKLIST = "blacklist:";


    /***
     * 离线
     */
    public static final String OFFLINE = "offline:";

    /***
     * 会话
     */
    public static final String SESSION = "session:";

    /***
     * 聊天会话
     */
    public static final String CHAT_SESSION = "chat-session:";


    /***
     * 好友请求
     */
    public static final String FRIEND_REQUEST = "friend-request:";

    /***
     * 正在处理中的好友请求会话标识
     */
    public static final String FRIEND_REQUEST_SESSION = "friend-request-session:";

    /***
     * 正在处理中的群请求会话标识
     */
    public static final String GROUP_REQUEST_SESSION = "group-request-session:";

    /***
     * 群请求
     */
    public static final String GROUP_REQUEST = "groups-request:";


    /***
     * 群用户请求
     */
    public static final String GROUP_USER_REQUEST = "group-users-request:";


    /***
     * mqtt
     */
    public static final String MQTT = "mqtt:";

    /***
     * topic
     */
    public static final String TOPIC = "topic:";

    /***
     * topic-list
     */
    public static final String TOPIC_LIST = "topic-list";


    /***
     * 设备 类型device-type
     */
    public static final String DEVICE_TYPE = "device-type";
}
