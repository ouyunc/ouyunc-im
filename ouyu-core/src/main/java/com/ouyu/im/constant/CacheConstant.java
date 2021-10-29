package com.ouyu.im.constant;

/**
 * @Author fangzhenxun
 * @Description: 缓存常量类
 * @Version V1.0
 **/
public class CacheConstant {
    // 冒号
    public static final String COLON  = ":";
    // 横杠
    public static final String CROSSBAR  = "-";
    // 公共前缀
    public static final String MESSAGE_COMMON_CACHE_PREFIX = "im:message:";
    // 群组
    public static final String GROUP_CACHE_PREFIX = "group:";
    // 单聊
    public static final String SINGLE_CACHE_PREFIX = "single:";
    // 历史消息
    public static final String HISTORY_CACHE_PREFIX = "history:";
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
    // 聊天的人员
    public static final String CHAT_CACHE_PREFIX = "chat:";
    // 用户相关
    public static final String USER_COMMON_CACHE_PREFIX = "im:user:";
    // 登录
    public static final String LOGIN_CACHE_PREFIX = "login:";
    // 广播类消息
    public static final String BROADCAST_CACHE_PREFIX = "broadcast:";



    // 历史消息缓存采用Timeline信箱，读扩散来进行设计；
    // 群聊====>   im:message:history:group:群id

}
