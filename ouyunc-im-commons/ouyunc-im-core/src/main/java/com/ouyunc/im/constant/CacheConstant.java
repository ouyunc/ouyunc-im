package com.ouyunc.im.constant;

/**
 * @Author fangzhenxun
 * @Description: 缓存相关常量类
 * @Version V3.0
 **/
public class CacheConstant {
    // 冒号
    public static final String COLON  = ":";
    // 横杠
    public static final String CROSSBAR  = "-";

    // 群组
    public static final String GROUP_CACHE_PREFIX = "group:";
    // 单聊
    public static final String SINGLE_CACHE_PREFIX = "single:";
    // 历史消息
    public static final String HISTORY_CACHE_PREFIX = "history:";
    // 发送失败消息
    public static final String FAIL_CACHE_PREFIX = "fail:";
    // 离线消息
    public static final String OFFLINE_CACHE_PREFIX = "offline:";
    // 新消息消息
    public static final String NEW_MESSAGE_CACHE_PREFIX = "new-message:";
    // 好友相关消息
    public static final String NEW_FRIEND_CACHE_PREFIX = "new-friend:";
    // 新会议消息
    public static final String NEW_MEETING_CACHE_PREFIX = "new-meeting:";
    // 消息发送者
    public static final String FROM_CACHE_PREFIX = "from:";
    // 消息接收者
    public static final String TO_CACHE_PREFIX = "to:";
    // 消息接收者/消息发送者唯一标识
    public static final String IDENTITY_CACHE_PREFIX = "identity:";
    // 消息包储存前缀
    public static final String PACKET_CACHE_PREFIX =  "packet:";
    // 联系人
    public static final String CONTACT_CACHE_PREFIX = "contact:";
    // 用户
    public static final String USER_CACHE_PREFIX = "user:";
    // 聊天的人员
    public static final String CHAT_CACHE_PREFIX = "chat:";

    // 登录
    public static final String LOGIN_CACHE_PREFIX = "login:";
    // 广播类消息
    public static final String BROADCAST_CACHE_PREFIX = "broadcast:";
    // 发送
    public static final String SEND_PREFIX = "send:";
    // 接收
    public static final String RECEIVER_PREFIX = "receiver:";

    // 集群服务下线hash key
    public static final String CLUSTER_SERVER_OFFLINE = "cluster:server:";


    // ouyunc 公共前缀
    public static final String COMMON_PREFIX  = "ouyunc:";
    //  im公共前缀
    public static final String IM_PREFIX  = "im:";

    // 集群服务离线相关前缀
    public static final String CLUSTER_SERVER_OFFLINE_CACHE_PREFIX  = "im:cluster:server:offline";

    // 消息相关公共前缀
    public static final String MESSAGE_COMMON_CACHE_PREFIX = "im:message:";
    // 用户相关公共前缀
    public static final String USER_COMMON_CACHE_PREFIX = "im:user:";

    // 用户登录相关前缀
    public static final String LOGIN = "login:";

    // 黑名单相关前缀
    public static final String BACK_LIST = "back-list:";

    // 用户联系人相关前缀
    public static final String CONTACT = "contact:";

    // 用户联系人（好友）相关前缀
    public static final String FRIEND = "friend:";

    // 群相关前缀
    public static final String GROUP = "group:";

    // 用户相关前缀
    public static final String USER = "user:";

    // 群成员相关前缀
    public static final String MEMBERS = "members:";

    // 发件箱消息相关前缀
    public static final String SEND = "send:";

    // 收件箱消息相关前缀
    public static final String RECEIVE = "receive:";

    // 离线消息关前缀
    public static final String OFFLINE = "offline:";

    // 全局失败消息关前缀
    public static final String FAIL = "fail:";

}
