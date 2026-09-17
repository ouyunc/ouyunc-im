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
     * HTTP 推送 toList 上限（排队变更一次扇出坐席）。
     */
    public static final int HTTP_PUSH_TO_LIST_MAX = 256;

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
     * 好友/群成员/拉黑 热路径布尔缓存容量。只存是否存在，短 TTL，靠写入侧标记与过期纠偏。
     */
    public static final long RELATION_PRESENCE_CACHE_MAX_SIZE = 500_000L;

    /**
     * 关系布尔缓存过期秒数。主动 Pub/Sub 失效为主；丢失时最多延迟这么久才靠过期纠偏。
     */
    public static final int RELATION_PRESENCE_CACHE_EXPIRE_SECONDS = 3;

    /**
     * 好友/群成员实体本机缓存过期秒数（配置读取；存在性不依赖此缓存）。
     */
    public static final int RELATION_ENTITY_LOCAL_CACHE_EXPIRE_SECONDS = 60;

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
     * 缓存最后一条会话消息 key / 会话 ZSet 过期时间，默认 30 天，单位毫秒。
     * 消息正文热 key 见 {@link #CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP}（更短）。
     */
    public static final long CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP = NumberConstant.NUMBER_30 * MessageConstant.DAY_TIMESTAMP;

    /** 客服会话路由 Redis TTL，与进行中咨询单生命周期一致 */
    public static final long CACHE_CS_SESSION_ROUTE_EXPIRE_TIMESTAMP = CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP;

    /**
     * 消息正文热缓存 TTL（P12）：短 TTL 控 Redis 大 value 内存；会话 ZSet / lm 仍用 {@link #CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP}。
     * 过期后读路径回源 Mongo/MySQL。
     */
    public static final long CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP = NumberConstant.NUMBER_2 * MessageConstant.HOUR_TIMESTAMP;

    /**
     * HTTP 推送 PENDING 僵死接管窗口（B3）：进程崩溃后同 messageId 可在该时限后重新抢占。
     */
    public static final long HTTP_PUSH_PENDING_TAKEOVER_MS = 30_000L;

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
     * appKey 设备类型变更频道（Redisson Topic）。
     * <p>payload：{@link com.ouyunc.base.model.AppKeyDeviceType} JSON 字符串。</p>
     */
    public static final String APP_KEY_PUBLISH_TOPIC = "app_key_publish_topic";
    /**
     * 客户端设备类型变更频道（Redisson Topic）。
     * <p>payload：{@link com.ouyunc.base.model.ClientAppKeyDeviceType} JSON 字符串。</p>
     */
    public static final String CLIENT_APP_KEY_PUBLISH_TOPIC = "client_app_key_publish_topic";

    /**
     * 集群成员发现：open 认租约；allowlist 仅连接 cluster.nodes ∪ topology
     */
    public static final String CLUSTER_MEMBERSHIP_MODE_OPEN = "open";

    public static final String CLUSTER_MEMBERSHIP_MODE_ALLOWLIST = "allowlist";

    /**
     * Redis 租约写失败时的隔离动作：摘流拒新登录，不自杀
     */
    public static final String CLUSTER_ISOLATION_ACTION_NONE = "none";

    public static final String CLUSTER_ISOLATION_ACTION_DRAIN_ON_REDIS_LOSS = "drain-on-redis-loss";

    /**
     * 从 Redis 发现的集群节点硬顶，防止错误 SET 灌爆连接
     */
    public static final int CLUSTER_MEMBERSHIP_MAX_NODES = 512;

    /**
     * IM 节点租约心跳任务 id
     */
    public static final String IM_NODE_LEASE_TASK_ID = "im-node-lease-heartbeat";

    /**
     * 节点租约刷新间隔（秒）
     */
    public static final int IM_NODE_LEASE_REFRESH_SECONDS = 2;

    /**
     * 节点租约 TTL（秒），须大于刷新间隔，kill -9 后整机在此窗口内判定宕机
     */
    public static final int IM_NODE_LEASE_TTL_SECONDS = 8;

    /**
     * 本机连接数变更后，合并写入 {nodeId} 连接 HASH 的等待（毫秒），避免每条登录打 Redis。
     */
    public static final int IM_NODE_CONN_PUBLISH_DEBOUNCE_MILLIS = 200;

    /**
     * 本机广播：每个 EventLoop 一次连续写出条数，写完再 execute 下一批，避免占满该 loop。
     */
    public static final int IM_LOCAL_BROADCAST_EVENTLOOP_BATCH = 64;

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
     * MQTT CONNECT password：{@code createTime#signature}，createTime 与原生登录签名同一套。
     */
    public static final char MQTT_LOGIN_PASSWORD_TIME_SEPARATOR = '#';

    /**
     * 解绑抢锁失败时的重试次数，避免幽灵 ONLINE。
     */
    public static final int BIND_LOCK_RETRY_TIMES = 3;

    /**
     * 解绑三次抢锁仍失败后，再延迟补偿一次。单位毫秒。
     */
    public static final long UNBIND_COMPENSATE_DELAY_MILLIS = 1000L;

    /**
     * 单连接有序业务队列上限。积压超过则关连，避免慢连接拖垮堆。
     */
    public static final int CHANNEL_ORDERED_TASK_MAX = 256;

    /**
     * 连接有序队列单任务 deadline（毫秒）。超时视为失败并推进下一条，避免永久卡住。
     */
    public static final long CHANNEL_ORDERED_TASK_DEADLINE_MS = 30_000L;

    /**
     * 连接有序调度被拒绝后的延迟重试（毫秒）；仍失败则关连清队列。
     */
    public static final long CHANNEL_ORDERED_SCHEDULE_RETRY_DELAY_MS = 50L;

    /**
     * Channel 写缓冲满（!isWritable）时延迟重试次数上限；超限再走 SEND_FAIL。
     */
    public static final int CHANNEL_WRITE_RETRY_MAX_ATTEMPTS = 8;

    /**
     * Channel 写缓冲满时首次延迟（毫秒）；后续按 attempt 线性递增。
     */
    public static final long CHANNEL_WRITE_RETRY_BASE_DELAY_MS = 20L;

    /**
     * 群成员 identity 列表本地缓存。热路径命中不打 Redis；入群/退群靠 Pub/Sub 立刻失效，丢失时最多延迟这么久。
     */
    public static final int GROUP_MEMBER_IDENTITY_CACHE_EXPIRE_SECONDS = 30;

    /** Redis 群成员 ZSET 游标分段 COUNT，避免大群一次 ZRANGE 0 -1 */
    public static final int GROUP_MEMBER_ZSET_SCAN_COUNT = 500;

    /** 群扇出：每批查在线/解析渠道的人数，避免一次物化万人 HashSet 与超大 Pipeline */
    public static final int GROUP_FANOUT_ONLINE_LOOKUP_BATCH = 500;

    /** 跨节点群扇出：单包携带的目标上限，超出则再拆包 */
    public static final int GROUP_FANOUT_REMOTE_TARGET_BATCH = 256;

    /** 本机群扇出：同一 EventLoop 每批发完后让出，控制单任务占用 */
    public static final int GROUP_FANOUT_LOCAL_EVENTLOOP_BATCH = 64;

    /** 消息按 ID 批量查询公共上限（Mongo/MySQL 分片入口） */
    public static final int MESSAGE_PACKET_QUERY_MAX_IDS = 500;

    /** Mongo 消息查询超时（毫秒），超时后按 error 降级 MySQL */
    public static final long MESSAGE_MONGO_QUERY_TIMEOUT_MS = 2_000L;

    /**
     * MQ Outbox：单条最大自动重试次数，超限置 DEAD。
     */
    public static final int MQ_OUTBOX_MAX_RETRY = 8;

    /**
     * MQ Outbox：每次扫描批量上限。
     */
    public static final int MQ_OUTBOX_RELAY_BATCH_SIZE = 32;

    /**
     * MQ Outbox：relay 首次延迟（毫秒）。
     */
    public static final long MQ_OUTBOX_RELAY_INITIAL_DELAY_MS = 3_000L;

    /**
     * MQ Outbox：relay 固定间隔（毫秒，fixed-delay）。
     */
    public static final long MQ_OUTBOX_RELAY_PERIOD_MS = 5_000L;

    /**
     * MQ Outbox：指数退避基数（毫秒）。
     */
    public static final long MQ_OUTBOX_BACKOFF_BASE_MS = 1_000L;

    /**
     * MQ Outbox：指数退避上限（毫秒）。
     */
    public static final long MQ_OUTBOX_BACKOFF_MAX_MS = 300_000L;

    /**
     * MQ Outbox：SENDING 超过该时长视为僵死，按 retry 回收或置 DEAD（毫秒）。
     */
    public static final long MQ_OUTBOX_SENDING_STALE_MS = 120_000L;

    /**
     * MQ Outbox：僵死 SENDING 回收时写入 last_error。
     */
    public static final String MQ_OUTBOX_STALE_SENDING_ERROR = "stale SENDING recycled";

    /**
     * MQ Outbox：ScheduleTimer 任务 id。
     */
    public static final String MQ_OUTBOX_RELAY_TASK_ID = "mq-outbox-relay";

    /**
     * MQ Outbox：集群扫描锁等待秒数。0 表示未抢到立即跳过，避免卡住 SYSTEM 定时线程。
     */
    public static final long MQ_OUTBOX_RELAY_LOCK_WAIT_SECONDS = 0L;

    /**
     * 登录超时：{@code IN_FLIGHT} 时最多再续期次数（每次仍为 {@code serverLoginTimeout} 秒）。
     */
    public static final int LOGIN_TIMEOUT_IN_FLIGHT_MAX_RETRY = 2;

    /**
     * MQTT 内容安全 REJECT 下行 topic（QoS0 PUBLISH）。
     */
    public static final String MQTT_SYS_NOTIFY_TOPIC = "$SYS/ouyunc/notify";

    /**
     * 用户实体本地缓存权重预算（近似字节）。与条数上限二选一用 weight。
     */
    public static final long USER_ENTITY_CACHE_MAX_WEIGHT = 256L * 1024 * 1024;

    /** 群成员配置实体本地缓存权重预算（近似字节） */
    public static final long GROUP_USER_ENTITY_CACHE_MAX_WEIGHT = 128L * 1024 * 1024;

    /** 群成员 identity Set 本地缓存权重预算（近似字节） */
    public static final long GROUP_MEMBER_IDENTITY_CACHE_MAX_WEIGHT = 64L * 1024 * 1024;

    /**
     * 群实体/成员配置本地缓存秒数。禁言、屏蔽等以 Redis 为准，禁止用 Pub/Sub 刷本地缓存。
     */
    public static final int GROUP_POLICY_LOCAL_CACHE_EXPIRE_SECONDS = 5;

    /**
     * 单用户可加入/创建的群数量上限；{@code -1} 表示不限制。
     */
    public static final int DEFAULT_GROUP_MAX_PER_USER = 500;

    /**
     * 单群成员数量上限；{@code -1} 表示不限制。
     */
    public static final int DEFAULT_GROUP_MAX_MEMBERS = 2000;

    /**
     * 群屏蔽 Hash 的初始化标记 field，避免空 Hash 与「尚未建索引」混淆。
     */
    public static final String GROUP_SHIELD_HASH_INIT_FIELD = "_i";

    /**
     * MQTT 报文标识最大值（1~65535）。
     */
    public static final int MQTT_PACKET_ID_MAX = 65535;

    /**
     * Mongo 双写补偿队列单次回放条数。
     */
    public static final int MONGO_COMPENSATE_BATCH_SIZE = 50;

    public static final String MONGO_COMPENSATE_KIND_MESSAGE = "message";
    public static final String MONGO_COMPENSATE_KIND_WITHDRAW = "withdraw";
    public static final String MONGO_COMPENSATE_KIND_OFFSET = "offset";
    public static final String MONGO_COMPENSATE_KIND_READ_RECEIPT = "read_receipt";

    /**
     * 热点群数量上限（按群 key，不是按成员条数）。
     */
    public static final long GROUP_MEMBER_IDENTITY_CACHE_MAX_SIZE = 50_000L;

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
     * 登录 Redis 校验进行中，防止同一连接并发打出两个登录。
     */
    public static final String CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT = "CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT";

    /**
     * 登录配额已本机预占（B5）：registerLocal 时不再二次 INCR；失败/关闭时释放。
     */
    public static final String CHANNEL_ATTR_KEY_CONN_QUOTA_RESERVED = "CHANNEL_ATTR_KEY_CONN_QUOTA_RESERVED";

    /**
     * 预占配额对应的 appKey，关连时按此 DECR。
     */
    public static final String CHANNEL_ATTR_KEY_CONN_QUOTA_APP_KEY = "CHANNEL_ATTR_KEY_CONN_QUOTA_APP_KEY";


    /**
     * channel 的登录超时调度器
     */
    public static final String CHANNEL_ATTR_KEY_LOGIN_TIMEOUT_SCHEDULED_FUTURE = "CHANNEL_ATTR_KEY_LOGIN_TIMEOUT_SCHEDULED_FUTURE";

    /**
     * 登录超时在 IN_FLIGHT 下已续期次数。
     */
    public static final String CHANNEL_ATTR_KEY_LOGIN_TIMEOUT_STRIKE = "CHANNEL_ATTR_KEY_LOGIN_TIMEOUT_STRIKE";

    /**
     * channel 关闭时的钩子标签
     */
    public static final String CHANNEL_ATTR_KEY_CHANNEL_CLOSE_HOOK = "CHANNEL_ATTR_KEY_CHANNEL_CLOSE_HOOK";

    /**
     * 链接上次的心跳间隔时间
     */
    public static final String CHANNEL_ATTR_KEY_TAG_HEARTBEAT_TIMEOUT = "CHANNEL_ATTR_KEY_TAG_HEARTBEAT_TIME";

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
     * 监控处理器（历史名）。敏感词改在 PacketHandler 连接有序任务内执行，不再挂管道。
     */
    public static final String MONITOR_HANDLER = "MONITOR_HANDLER";

    /**
     * 统一登录认证处理器
     */
    public static final String AUTHENTICATION_HANDLER = "AUTHENTICATION_HANDLER";

    /**
     * MQTT 等非 LOGIN 协议的连接登录超时（CONNECT 完成前关连）。
     */
    public static final String LOGIN_TIMEOUT_HANDLER = "LOGIN_TIMEOUT_HANDLER";

    /**
     * 统一 Packet 业务入口（客户端 / 集群均挂 {@code PacketHandler}）。
     */
    public static final String PACKET_HANDLER = "PACKET_HANDLER";

    /**
     * 客户端 SDK：WebSocket 业务 Handler 名（{@code WsProtocolHandler}），与服务端 {@link #PACKET_HANDLER} 不同。
     */
    public static final String WS_HANDLER = "WS_HANDLER";


    /**
     * ouyunc 业务处理器（集群管道上的 {@code PacketHandler.cluster()}）
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
     * 单条消息内容最大长度（字节）：256KB。图片/文件走对象存储，聊天包禁止把 EventLoop 缓冲打到 MB 级。
     */
    public static final int MAX_MESSAGE_CONTENT_LENGTH = 256 * 1024;

    /**
     * LengthFieldBasedFrameDecoder 最大帧长（字节）：协议头 + 消息体上限
     * Why: 与 {@link #MAX_MESSAGE_CONTENT_LENGTH} 对齐，避免 length 域合法但整帧超大导致 OOM
     */
    public static final int MAX_FRAME_LENGTH = MAX_MESSAGE_CONTENT_LENGTH + PACKET_BASE_LENGTH;






    /**
     * 同一设备类型，异设备（sn 不同）远程登录通知
     */
    public static final String REMOTE_LOGIN_NOTIFICATIONS = "你的ouyunc账号在另一台设备(ip: %s)上登录，你已被迫下线。如果本人不知晓，请立即冻结账号，并及时修改相关密码。";

    /**
     * 同 sn 跨节点顶号：旧连接静默下线提示（不强调“异设备”）
     */
    public static final String REMOTE_LOGIN_SAME_DEVICE_KICK = "同设备重新登录，旧连接已下线。";

    /**
     * 服务滚动升级：通知客户端主动断开并重连其他节点（服务端不主动 close）
     */
    public static final String SERVER_DRAIN_KICK_NOTIFICATION = "服务节点正在维护升级，请主动断开当前连接并重连；重连成功前请暂停发送消息。";

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
