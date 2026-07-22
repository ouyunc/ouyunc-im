package com.ouyunc.base.constant;

/**
 * @Author fzx
 * @Description: 常量类
 **/
public class MessageConstant {

    /***
     * ouyunc 公共前缀
     */
    public static final String OUYUNC = "ouyunc";

    /**
     * 租户 appKey 默认值（metadata / 请求未传时使用）。
     */
    public static final String DEFAULT_APP_KEY = OUYUNC;

    /**
     * 下划线
     */
    public static final String UNDERLINE = "_";

    /**
     * 0.5
     */
    public static final Float ZERO_POINT_FIVE = 0.5f;

    /**
     * 锁等待时间 5 s
     */
    public static final long LOCK_WAIT_TIME = 5;

    /**
     * 锁持有时间 30 s
     */
    public static final long LOCK_LEASE_TIME = 5;


    /**
     * 0 字符串
     */
    public static final String ZERO_STR = "0";

    /**
     * 1 字符串
     */
    public static final String ONE_STR = "1";

    /**
     * 数字1000
     */
    public static final int NUMBER_1000 = 1000;

    /**
     * 数字1024
     */
    public static final int NUMBER_1024 = 1024;

    /**
     * 数字5000
     */
    public static final int NUMBER_5000 = 5000;

    /**
     * 数字10000
     */
    public static final int NUMBER_10000 = 10000;

    /**
     * 本地缓存最大容量120万
     */
    public static final long LOCAL_CACHE_MAX_SIZE = 1_200_000;

    /**
     * QoS/调度定时任务本地缓存最大条目数。
     * <p>按 100 万在线、人均 5 条/分钟、QoS SERVER 重试估算：峰值 QoS QPS ≈ 83333/s；
     * 默认重试窗口约 12s（3s 起 + 3 次 × 3s），无 ACK 时并发任务 ≈ 100 万；ACK 约 2s 时 ≈ 17 万。
     * 任务体为 {@link com.ouyunc.message.schedule.QosRetryTaskContext}，单条约数百字节。</p>
     */
    public static final int TIMER_TASK_CACHE_MAX_SIZE = 1_000_000;


    /**
     * 一天的时间戳 毫秒
     */
    public static final long DAY_TIMESTAMP = 24*60*60*1000L;



    /**
     * 一小时的时间戳， 毫秒
     */
    public static final long HOUR_TIMESTAMP = 60*60*1000L;


    /**
     * 一分钟的时间戳
     */
    public static final long MINUTE_TIMESTAMP = 60*1000L;

    /**
     * 一秒的时间戳
     */
    public static final long SECOND_TIMESTAMP = NUMBER_1000;

    /**
     * false
     */
    public static final boolean FALSE = false;

    /**
     * true
     */
    public static final boolean TRUE = true;


    /**
     * 默认的websocket 缓存大小
     */
    public static final int MAX_WEBSOCKET_FRAME_SIZE = 2 * 1024 * 1024; // 2MB

    /**
     * 默认的websocket 压缩阈值
     */
    public static final int WEBSOCKET_COMPRESSION_THRESHOLD = 4 * 1024; // 4KB

    /**
     * 最大撤回消息数量，默认50
     */
    public static final int MAX_WITHDRAW_MESSAGE_COUNT = 50;

    /**
     * 允许撤回的消息时间窗口，默认 2 分钟，单位毫秒（以服务端到达时间为准）
     */
    public static final long WITHDRAW_MESSAGE_TIME_WINDOW_MS = 2 * MINUTE_TIMESTAMP;

    /**
     * 最大已读回执消息数量，默认50
     */
    public static final int MAX_READ_RECEIPT_MESSAGE_COUNT = 50;

    /**
     * 群聊 @ 人数上限（含 @全体成员 占位符计 1 个）
     */
    public static final int MAX_AT_TARGET_COUNT = 50;

    /**
     * 单条消息引用（ref）条数上限
     */
    public static final int MAX_REF_COUNT = 5;

    /**
     *  缓存最后一条会话消息 key / 会话 ZSet / 消息热 key 过期时间，默认 30 天，单位毫秒
     */
    public static final long CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP = NumberConstant.NUMBER_30 * MessageConstant.DAY_TIMESTAMP;

    /** 客服会话路由 Redis TTL，与进行中咨询单生命周期一致 */
    public static final long CACHE_CS_SESSION_ROUTE_EXPIRE_TIMESTAMP = CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP;

    /**
     *  缓存消息热 key 过期时间（与会话 ZSet 一致）
     */
    public static final long CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP = CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP;

    /**
     * QoS 幂等（packetId）缓存过期时间，默认 30 分钟
     */
    public static final long CACHE_QOS_IDEM_PACKET_EXPIRE_TIMESTAMP = NumberConstant.NUMBER_30 * MessageConstant.MINUTE_TIMESTAMP;

    /**
     * QoS 幂等（客户端 messageId）缓存过期时间，默认 5 分钟
     */
    public static final long CACHE_QOS_IDEM_CLIENT_EXPIRE_TIMESTAMP = NumberConstant.NUMBER_5 * MessageConstant.MINUTE_TIMESTAMP;


    /**
     *  缓存请求会话key 过期时间默认30 天，与mongo 保持一致
     */
    public static final long CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP = NumberConstant.NUMBER_30 * MessageConstant.DAY_TIMESTAMP;


    /**
     *   缓存消息已读回执的过期时间戳，30天,时间戳，单位毫秒
     */
    public static final long CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP = NumberConstant.NUMBER_30 * MessageConstant.DAY_TIMESTAMP;

    /** 单聊未读 Hash 存储上限（超过视为 capped） */
    public static final int SESSION_UNREAD_STORE_MAX = 1000;

    /** 单聊未读展示上限 */
    public static final int SESSION_UNREAD_DISPLAY_CAP = 99;

    /** 单聊未读 ZSet 扫描降级上限（冷路径） */
    public static final int SESSION_UNREAD_PEER_SCAN_LIMIT = 200;

    /** @deprecated 使用 {@link #SESSION_UNREAD_STORE_MAX} */
    public static final int SESSION_PEER_UNREAD_INDEX_MAX_SIZE = SESSION_UNREAD_STORE_MAX;

    /** @deprecated 使用 {@link #SESSION_UNREAD_DISPLAY_CAP} */
    public static final int SESSION_PEER_UNREAD_DISPLAY_CAP = SESSION_UNREAD_DISPLAY_CAP;

    /** 用户设备单聊未读 Hash TTL，与热会话一致 */
    public static final long CACHE_USER_DEVICE_UNREAD_EXPIRE_TIMESTAMP = CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP;



    /**
     *   缓存好友配置信息的过期时间戳，30天,时间戳，单位毫秒
     */
    public static final long CACHE_FRIENDS_CONFIG_KEY_EXPIRE_TIMESTAMP = NumberConstant.NUMBER_30 * MessageConstant.DAY_TIMESTAMP;



    /**
     *   mongo好友配置信息的过期时间戳，90天,时间戳，单位毫秒
     */
    public static final long MONGO_FRIENDS_CONFIG_KEY_EXPIRE_DAY = NumberConstant.NUMBER_90;



    /**
     *   缓存群成员配置信息的过期时间戳，30天,时间戳，单位毫秒
     */
    public static final long CACHE_GROUP_USER_CONFIG_KEY_EXPIRE_TIMESTAMP = NumberConstant.NUMBER_30 * MessageConstant.DAY_TIMESTAMP;

    /**
     *  通用实体缓存（friend/group/groupUser/sessionOffset）的 TTL，30 天，单位毫秒
     *  Why: 防止 Redis Key 永不过期导致内存无界增长
     */
    public static final long CACHE_ENTITY_KEY_EXPIRE_TIMESTAMP = NumberConstant.NUMBER_30 * MessageConstant.DAY_TIMESTAMP;

    /**
     *  会话 ZSet 最大保留消息数（按 packetId LEX 排序，仅保留最新 N 条）
     *  Why: 防止活跃会话 ZSet 无界增长（每条消息 ZADD 一次，30 天 TTL 每次写入刷新）
     *  How: 写入后执行 ZREMRANGEBYRANK key 0 -(MAX+1) 裁剪较老条目
     */
    public static final int SESSION_ZSET_MAX_SIZE = 5000;


    /**
     *  app key  变动 publish topic
     */
    public static final String APP_KEY_PUBLISH_TOPIC = "app_key_publish_topic";
    /**
     *  客户端 的 app key  变动 publish topic
     */
    public static final String CLIENT_APP_KEY_PUBLISH_TOPIC = "client_app_key_publish_topic";

    /**
     * appKey 连接数 ZSet 过期清理定时任务 id（与 ScheduleTimer 注册 id 一致）
     */
    public static final String APP_KEY_CONNECTION_COUNT_REFRESH_TASK_ID = "appKey-connection-count-refresh-timer";

    /**
     * 自定义协议的魔数6个字节,字节数组 OUYUNC
     */
    public static final byte[] PACKET_MAGIC_BYTES = {
            (byte) 0x4F,//十进制79（O）
            (byte) 0x55,//十进制85（U）
            (byte) 0x59,//十进制89（Y）
            (byte) 0x55,//十进制85（U）
            (byte) 0x4E,//十进制78（N）
            (byte) 0x43//十进制67 （C）
    };

    /**
     *  OUYUNC
     */
    public static final String PACKET_MAGIC = "OUYUNC";

    /**
     *urf-8
     */
    public static final String UTF_8 = "UTF-8";

    /**
     * 冒号分割符
     */
    public static final String COLON = ":";

    /**
     * 登录签名字段分隔符：{@code appKey & identity & createTime_appSecret}
     */
    public static final String LOGIN_SIGNATURE_FIELD_SEPARATOR = "&";

    /**
     * 登录签名 createTime 允许的时钟偏差（毫秒），默认 ±5 分钟
     */
    public static final long LOGIN_SIGNATURE_CREATE_TIME_SKEW_MS = 5L * 60L * 1000L;

    /**
     * 广播接收方占位符（全员 SERVER_NOTIFY）
     */
    public static final String SPLAT = "*";

    /**
     * 请求头 X_REAL_IP key
     */
    public static final String HEADER_X_REAL_IP = "X-Real-IP";

    /**
     * 消息应用名称
     */
    public static final String DEFAULT_APPLICATION_NAME = "ouyunc-message";


    //====================================channel attr tag=============================================


    /**
     * BOOTSTRAP 客户端属性标签，标识该启动类下的都属于集群属性
     */
    public static final String BOOTSTRAP_ATTR_KEY_TAG_CLIENT = "BOOTSTRAP_ATTR_KEY_TAG_CLIENT";


    /**
     * BOOTSTRAP 集群客户端属性标签值
     */
    public static final String BOOTSTRAP_ATTR_KEY_TAG_CLUSTER_CLIENT_VALUE = "OUYUNC_CLUSTER_CLIENT";

    /**
     * ctx 的协议类型标签,这个非常关键
     */
    public static final String CHANNEL_ATTR_KEY_TAG_PROTOCOL_TYPE = "PROTOCOL_TYPE";

    /**
     * channel 的haproxy protocol 协议标签存放代理后的真实客户端的代理信息  HAProxyMessage
     */
    public static final String CHANNEL_ATTR_KEY_TAG_CLIENT_REAL_IP = "CHANNEL_ATTR_KEY_TAG_CLIENT_REAL_IP";

    /**
     * channel 的登录标签，存放的是LoginUserInfo对象
     */
    public static final String CHANNEL_ATTR_KEY_TAG_POOL = "CHANNEL_ATTR_KEY_TAG_POOL";


    /**
     * channel 的登录标签，存放的是LoginUserInfo对象
     */
    public static final String CHANNEL_ATTR_KEY_TAG_LOGIN = "CHANNEL_ATTR_KEY_TAG_LOGIN";


    /**
     * channel 的登录超时调度器
     */
    public static final String CHANNEL_ATTR_KEY_LOGIN_TIMEOUT_SCHEDULED_FUTURE = "CHANNEL_ATTR_KEY_LOGIN_TIMEOUT_SCHEDULED_FUTURE";

    /**
     * channel 关闭时的钩子标签
     */
    public static final String CHANNEL_ATTR_KEY_CHANNEL_CLOSE_HOOK = "CHANNEL_ATTR_KEY_CHANNEL_CLOSE_HOOK";

    /**
     * 该链接上次的心跳时间戳
     */
    public static final String CHANNEL_ATTR_KEY_TAG_LAST_HEARTBEAT_TIMESTAMP = "CHANNEL_ATTR_KEY_TAG_LAST_HEARTBEAT_TIMESTAMP";

    /**
     * 链接上次的心跳间隔时间
     */
    public static final String CHANNEL_ATTR_KEY_TAG_HEARTBEAT_TIMEOUT = "CHANNEL_ATTR_KEY_TAG_HEARTBEAT_TIME";

    /**
     * channel 下一次允许执行保活刷新的时间戳（毫秒）
     */
    public static final String CHANNEL_ATTR_KEY_TAG_NEXT_KEEP_ALIVE_REFRESH_TIMESTAMP = "CHANNEL_ATTR_KEY_TAG_NEXT_KEEP_ALIVE_REFRESH_TIMESTAMP";


    /**
     * channel 客户端读超时的次数标签
     */
    public static final String CHANNEL_ATTR_KEY_TAG_READ_TIMEOUT_TIMES = "CHANNEL_ATTR_KEY_TAG_CLIENT_READ_TIMEOUT_TIMES";

    /**
     * 读空闲关闭连接前的最大重试次数（与全局 {@code client.heart-beat.wait-retry} 同语义）；登录传入 {@code heartBeatWaitRetry>0} 时写入并覆盖全局，否则由  仅用服务端配置
     */
    public static final String CHANNEL_ATTR_KEY_TAG_HEARTBEAT_WAIT_RETRY = "CHANNEL_ATTR_KEY_TAG_HEARTBEAT_WAIT_RETRY";

    /**
     * 连续业务读空闲档次数（非 PING 业务上行时清零）；用于提示 / 托管 / 关连三档
     */
    public static final String CHANNEL_ATTR_KEY_TAG_BUSINESS_IDLE_STRIKE = "CHANNEL_ATTR_KEY_TAG_BUSINESS_IDLE_STRIKE";


    /**
     * qos dup 的原始packet
     */
    public static final String CHANNEL_ATTR_KEY_QOS_DUP_ORIGINAL_PACKET = "CHANNEL_ATTR_KEY_QOS_DUP_ORIGINAL_PACKET";


    // ==============================================handler tag=====================================


    /**
     * SSL/TLS 处理器标识
     */
    public static final String SSL_HANDLER = "SSL_HANDLER";


    /**
     * 日志 处理器标识
     */
    public static final String LOG_HANDLER = "LOG_HANDLER";

    /**
     * proxy protocol 处理器标识，用于解析代理后的客户端真实ip
     */
    public static final String HA_PROXY_PROTOCOL_DECODER_HANDLER = "HA_PROXY_PROTOCOL_DECODER_HANDLER";

    /**
     * 处理客户端真实ip
     */
    public static final String REMOTE_CLIENT_REAL_IP_HANDLER = "REMOTE_CLIENT_REAL_IP_HANDLER";



    /**
     * 协议调度分发器
     */
    public static final String PROTOCOL_DISPATCHER_HANDLER = "PROTOCOL_DISPATCHER_HANDLER";




    /**
     * http 服务的编解码处理器
     */
    public static final String HTTP_SERVER_CODEC_HANDLER = "HTTP_SERVER_CODEC_HANDLER";

    /**
     * 分块向客户端写数据
     */
    public static final String CHUNKED_WRITE_HANDLER = "CHUNKED_WRITE_HANDLER";

    /**
     * HttpMessage和HttpContents聚合
     */
    public static final String HTTP_OBJECT_AGGREGATOR_HANDLER = "HTTP_OBJECT_AGGREGATOR_HANDLER";

    /**
     * HttpObjectAggregator 超长等异常转 413 JSON（紧接在聚合器之后）
     */
    public static final String HTTP_AGGREGATOR_EXCEPTION_HANDLER = "HTTP_AGGREGATOR_EXCEPTION_HANDLER";

    /**
     * http 调度处理器
     */
    public static final String HTTP_DISPATCHER_HANDLER = "HTTP_DISPATCHER_HANDLER";



    /**
     * packet 调度处理器
     */
    public static final String PACKET_DISPATCHER_HANDLER = "PACKET_DISPATCHER_HANDLER";



    /**
     * ws 聚合 websocket 的数据帧
     */
    public static final String WS_FRAME_AGGREGATOR_HANDLER = "WS_FRAME_AGGREGATOR_HANDLER";

    /**
     * ws 聚合 websocket 的数据压缩
     */
    public static final String WS_COMPRESSION_HANDLER = "WS_COMPRESSION_HANDLER";

    /**
     * ws 向外暴漏服务地址
     */
    public static final String WS_SERVER_PROTOCOL_HANDLER = "WS_SERVER_PROTOCOL_HANDLER";



    /**
     * 转换为packet处理器
     */
    public static final String CONVERT_2_PACKET_HANDLER = "CONVERT_2_PACKET_HANDLER";

    /**
     * 客户端登录保活处理器
     */
    public static final String CLIENT_LOGIN_KEEP_ALIVE_HANDLER = "LOGIN_KEEP_ALIVE_HANDLER";


    /**
     * mqtt 编码器处理器
     */
    public static final String MQTT_ENCODER_HANDLER = "MQTT_ENCODER_HANDLER";

    /**
     * mqtt 解码器处理器
     */
    public static final String MQTT_DECODER_HANDLER = "MQTT_DECODER_HANDLER";

    /**
     * mqtt 业务处理器
     */
    public static final String MQTT_SERVER_HANDLER = "MQTT_SERVER_HANDLER";

    /**
     * mqtt 调度处理器
     */
    public static final String MQTT_DISPATCHER_HANDLER = "MQTT_DISPATCHER_HANDLER";

    /**
     * mqtt websocket 处理器
     */
    public static final String MQTT_WEBSOCKET_CODEC_HANDLER = "MQTT_WEBSOCKET_CODEC_HANDLER";

    /**
     * 心跳读空闲：第一个 {@link io.netty.handler.timeout.IdleStateHandler}（连接/心跳周期）
     */
    public static final String HEART_BEAT_IDLE_HANDLER = "HEART_BEAT_IDLE_HANDLER";

    /**
     * 业务读空闲：{@code PingAwareBusinessIdleStateHandler}（继承 IdleStateHandler，登录 {@code businessIdleSeconds}），紧接在 {@link #HEART_BEAT_HANDLER} 之后，或紧接 {@link #CONVERT_2_PACKET_HANDLER}（无全局心跳时）
     */
    public static final String BUSINESS_READ_IDLE_HANDLER = "BUSINESS_READ_IDLE_HANDLER";

    /**
     * 心跳处理器
     */
    public static final String HEART_BEAT_HANDLER = "HEART_BEAT_HANDLER";


    /**
     * 监控 处理器
     */
    public static final String MONITOR_HANDLER = "MONITOR_HANDLER";




    /**
     * 统一登录认证处理器
     */
    public static final String AUTHENTICATION_HANDLER = "AUTHENTICATION_HANDLER";

    /**
     * 统一前置处理器
     */
    public static final String PRE_HANDLER = "PRE_HANDLER";



    /**
     * post 处理器
     */
    public static final String POST_HANDLER = "POST_HANDLER";


    /**
     * ws 业务处理器
     */
    public static final String WS_HANDLER = "WS_HANDLER";


    /**
     * ouyunc 业务处理器
     */
    public static final String OUYUNC_HANDLER = "OUYUNC_HANDLER";

    /**
     * 集群中packet 路由处理器
     */
    public static final String PACKET_CLUSTER_ROUTER_HANDLER = "PACKET_CLUSTER_ROUTER_HANDLER";


    /**
     * 全局异常处理器
     */
    public static final String EXCEPTION_HANDLER = "GLOBAL_EXCEPTION_HANDLER";

    /**
     * packet 粘包/半包
     */
    public static final String PACKET_DECODE_HANDLER = "PACKET_DECODE_HANDLER";

    /**
     * packet 包解码
     */
    public static final String PACKET_CODEC_HANDLER = "PACKET_CODEC_HANDLER";



    /**
     * 内置客户端心跳
     */
    public static final String CLIENT_HEART_BEAT_HANDLER = "CLIENT_HEART_BEAT_HANDLER";


    /**
     * 内置客户端空闲事件
     */
    public static final String CLIENT_IDLE_HANDLER = "CLIENT_IDLE_HANDLER";

    /**
     * 内置客户端包编码
     */
    public static final String CLIENT_PACKET_CODEC_HANDLER = "CLIENT_PACKET_CODEC_HANDLER";














    //====================================================protocol====================================

    /**
     * http 升级websocket 协议的请求upgrade
     */
    public static final String WEBSOCKET_PROTOCOL_UPGRADE = "WEBSOCKET";

    /**
     * http 升级websocket 协议的请求connect
     */
    public static final String WEBSOCKET_PROTOCOL_CONNECTION = "UPGRADE";

    /**
     * SEC_WEBSOCKET_PROTOCOL
     */
    public static final String SEC_WEBSOCKET_PROTOCOL = "sec-websocket-protocol";


    /**
     * 定义mqtt 的 websocket支持的子协议，如果多个使用英文逗号隔开
     */
    public static final String MQTT_WEBSOCKET_SUB_PROTOCOLS = "mqtt,mqttv3.1";


    /**
     * mqtt
     */
    public static final String MQTT = "mqtt";

    /**
     * mqtt31
     */
    public static final String MQTT_3_1 = "mqttv3.1";


    /**
     * LengthFieldBasedFrameDecoder 内容长度字段所占的字节数
     */
    public static final int LENGTH_FIELD_LENGTH = 4;

    /**
     * |    6   |     1   |    1    |     8    |    1     |    1      |    1     |     1     |     1     |     1     |      4    |    n     |
     * +---------+---------+---------+----------+----------+----------+-----------+----------+-----------+-----------+-----------+----------+-
     * |         |         |         |          |          |          |           |          |           |           |           |          |
     * |  魔数    |  协议类型| 协议版本  | 协议包id  | 设备类型   | 网络类型  | 加密算法    | 序列化算法 |  消息类型   | 保留字段   |  消息长度   |   消息体  |
     * |         |         |         |          |          |          |           |          |           |           |           |          |
     * +---------+---------+---------+----------+----------+----------+-----------+----------+-----------+-----------+-----------+----------+-
     * 参数含义及如何设置： 可参看 https://blog.csdn.net/hxj413977035/article/details/121633308
     */

    /**
     * LengthFieldBasedFrameDecoder 消息长度字段的 偏移字节，这里是固定协议头大小（21字节）
     */
    public static final int LENGTH_FIELD_OFFSET = 22;
    /**
     * LengthFieldBasedFrameDecoder  修改帧数据长度字段中定义的值，可以为负数 因为有时候我们习惯把头部记入长度,若为负数,则说明要推后多少个字段
     */
    public static final int LENGTH_ADJUSTMENT = 0;
    /**
     * LengthFieldBasedFrameDecoder 解析时候跳过多少个长度
     */
    public static final int INITIAL_BYTES_TO_STRIP = 0;
    /**
     * LengthFieldBasedFrameDecoder ，如果为true，则表示读取到长度域，TA的值的超过maxFrameLength，就抛出一个 TooLongFrameException，而为false表示只有当真正读取完长度域的值表示的字节之后，才会抛出 TooLongFrameException，默认情况下设置为true，建议不要修改，否则可能会造成内存溢出
     */
    public static final boolean FAIL_FAST = true;

    /**
     * packet 中魔数所占字节数
     */
    public static final int MAGIC_BYTE_LENGTH = PACKET_MAGIC_BYTES.length;


    /**
     * 协议基础长度
     */
    public static final int PACKET_BASE_LENGTH = LENGTH_FIELD_OFFSET + LENGTH_FIELD_LENGTH;

    /**
     * 单条消息内容最大长度（字节）：10MB
     * Why: 防止恶意客户端发送超大 messageLength 触发 OOM
     */
    public static final int MAX_MESSAGE_CONTENT_LENGTH = 10 * 1024 * 1024;

    /**
     * LengthFieldBasedFrameDecoder 最大帧长（字节）：协议头 + 消息体上限
     * Why: 与 {@link #MAX_MESSAGE_CONTENT_LENGTH} 对齐，避免 length 域合法但整帧超大导致 OOM
     */
    public static final int MAX_FRAME_LENGTH = MAX_MESSAGE_CONTENT_LENGTH + PACKET_BASE_LENGTH;






    /**
     * 同一设备类型，异设备（sn 不同）远程登录通知
     */
    public static final String REMOTE_LOGIN_NOTIFICATIONS = "你的ouyunc账号在另一台设备(ip: %s)上登录，你已被迫下线。如果本人不知晓，请立即冻结账号，并及时修改相关密码。";

    /** 客服座席：首次业务读空闲提示（scope=cs_agent） */
    public static final String BUSINESS_IDLE_PROMPT_CS_AGENT =
            "您已有一段时间未操作，会话仍保持连接；请继续处理咨询或发送消息以保持在线。";

    /** 客服访客：首次业务读空闲提示（scope=cs_visitor） */
    public static final String BUSINESS_IDLE_PROMPT_CS_VISITOR =
            "您已有一段时间未发送消息，如需继续咨询请直接输入。";

    /** 第 2 次业务空闲且仍将关连：%d 为 {@code businessIdleSeconds} */
    public static final String BUSINESS_IDLE_PRE_CLOSE =
            "长时间无操作，若 %d 秒内仍无消息，连接将自动断开。";

    /** 第 2 次业务空闲但不关连（{@code businessIdleCloseStrike <= 0} 或已达末档前） */
    public static final String BUSINESS_IDLE_REPEAT_PROMPT =
            "长时间无操作，请发送任意消息以保持连接。";

    /**
     * IM → CS 坐席通道 presence 事件类型（Kafka {@code ouyunc-cs-agent-presence}），
     * 与 CS 侧 {@code CsAgentPresenceEventTypes} 取值一致。
     */
    public static final String CS_AGENT_PRESENCE_CHANNEL_CLOSE = "CHANNEL_CLOSE";

    /** 坐席 IM 通道打开 / 登录 bind 成功 */
    public static final String CS_AGENT_PRESENCE_CHANNEL_OPEN = "CHANNEL_OPEN";

}
