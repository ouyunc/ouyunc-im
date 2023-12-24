package com.ouyunc.im.constant;

/**
 * @Author fangzhenxun
 * @Description: channel 通道相关的常量类
 **/
public class IMConstant {
    /**
     * 0
     */
    public static final Integer ZERO = 0;

    /**
     * -1
     */
    public static final Integer MINUS_ONE = -1;
    /**
     *urf-8
     */
    public static final String UTF_8 = "UTF-8";

    /**
     * 自定义协议的魔数,十进制102
     */
    public static final byte PACKET_MAGIC = 0x66;


    /**
     * channel 标签key channel 的池hashcode
     */
    public static final String CHANNEL_TAG_POOL = "CHANNEL_TAG_POOL";

    /**
     * channel 的登录标签，存放的是LoginUserInfo对象
     */
    public static final String CHANNEL_TAG_LOGIN = "CHANNEL_TAG_LOGIN";

    /**
     * channel 所绑定的app key
     */
    public static final String APP_KEY = "APP_KEY";

    /**
     * channel 客户端读超时的次数标签
     */
    public static final String CHANNEL_TAG_READ_TIMEOUT = "CHANNEL_TAG_READ_TIMEOUT";


    /**
     * 冒号分割符
     */
    public static final String COLON_SPLIT = ":";

    /**
     * 下划线
     */
    public static final String UNDER_LINE = "_";

    /**
     * 中划线
     */
    public static final String MIDDLE_LINE = "-";



    /**
     * &符号
     */
    public static final String AND = "&";
    /**
     * 下划线
     */
    public static final String COMMA = ",";

    /**
     * 本地ip 可以
     */
    public static final String LOCAL_ADDRESS_KEY = "local-address";

    /**
     * 本地ip
     */
    public static final String LOCAL_HOST = "127.0.0.1";

    /**
     * 开启ssl
     */
    public static final byte OPEN_SSL = 1;

    /**
     * 不开启ssl
     */
    public static final byte NO_OPEN_SSL = 0;

    /**
     * LengthFieldBasedFrameDecoder 最大长度
     */
    public static final int MAX_FRAME_LENGTH = Integer.MAX_VALUE;
    /**
     * LengthFieldBasedFrameDecoder 内容长度字段所占的字节数
     */
    public static final int LENGTH_FIELD_LENGTH = 4;
    /**
     * LengthFieldBasedFrameDecoder 长度偏移，这里是固定协议头大小（16字节）+消息内容大小（4字节）
     */
    public static final int LENGTH_FIELD_OFFSET = 20;
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
    public static final int MAGIC_BYTES = 1;


    /**
     * 协议基础长度
     */
    public static final int PACKET_BASE_LENGTH = LENGTH_FIELD_OFFSET + LENGTH_FIELD_LENGTH;


    /**
     * 统一登录处理器
     */
    public static final String PRE_HANDLER = "AUTHENTICATION";

    /**
     * 转换为packet处理器
     */
    public static final String CONVERT_2_PACKET = "CONVERT_2_PACKET";

    /**
     * SSL/TLS 处理器
     */
    public static final String SSL = "SSL";

    /**
     * 监控 处理器
     */
    public static final String MONITOR_HANDLER = "MONITOR_HANDLER";



    /**
     * 协议调度分发器
     */
    public static final String PROTOCOL_DISPATCHER = "PROTOCOL_DISPATCHER";

    /**
     * 内置客户端心跳
     */
    public static final String INNER_CLIENT_HEART_BEAT = "INNER_CLIENT_HEART_BEAT";


    /**
     * 内置客户端空闲事件
     */
    public static final String INNER_CLIENT_IDLE = "INNER_CLIENT_IDLE";

    /**
     * 内置客户端包编码
     */
    public static final String INNER_CLIENT_PACKET_CODEC = "INNER_CLIENT_PACKET_CODEC";


    /**
     * ws协议日志
     */
    public static final String LOG = "LOG";

    /**
     * ws 中 http 服务的编解码处理器
     */
    public static final String HTTP_SERVER_CODEC = "HTTP_SERVER_CODEC";

    /**
     * ws 业务处理器
     */
    public static final String WS_HANDLER = "WS_HANDLER";

    /**
     * ws 分块向客户端写数据
     */
    public static final String CHUNKED_WRITE_HANDLER = "CHUNKED_WRITE_HANDLER";

    /**
     * ws 将HttpMessage和HttpContents聚合
     */
    public static final String HTTP_OBJECT_AGGREGATOR = "HTTP_OBJECT_AGGREGATOR";


    /**
     * ws 聚合 websocket 的数据帧
     */
    public static final String WS_FRAME_AGGREGATOR = "WS_FRAME_AGGREGATOR";

    /**
     * ws 向外暴漏服务地址
     */
    public static final String WS_SERVER_PROTOCOL_HANDLER = "WS_SERVER_PROTOCOL_HANDLER";

    /**
     * http 调度处理器
     */
    public static final String HTTP_DISPATCHER_HANDLER = "HTTP_DISPATCHER_HANDLER";


    /**
     * packet 粘包/半包
     */
    public static final String PACKET_DECODE = "PACKET_DECODE";

    /**
     * packet 包解码
     */
    public static final String PACKET_CODEC = "PACKET_CODEC";

    /**
     * packet 调度处理器
     */
    public static final String PACKET_DISPATCHER_HANDLER = "PACKET_DISPATCHER_HANDLER";

    /**
     * http 升级websocket 协议的请求upgrade
     */
    public static final String WEBSOCKET_PROTOCOL_UPGRADE = "WEBSOCKET";

    /**
     * http 升级websocket 协议的请求connect
     */
    public static final String WEBSOCKET_PROTOCOL_CONNECTION = "UPGRADE";

    /**
     * 全局异常处理器
     */
    public static final String GLOBAL_EXCEPTION = "GLOBAL_EXCEPTION";

    /**
     * 自定义偶遇im 处理器
     */
    public static final String OUYUNC_IM_HANDLER = "OUYUNC_IM_HANDLER";


    /**
     * 外部客户端的心跳空闲处理
     */
    public static final String HEART_BEAT_IDLE = "HEART_BEAT_IDLE";

    /**
     * 外部客户端的心跳空闲处理
     */
    public static final String HEART_BEAT_HANDLER = "HEART_BEAT_HANDLER";

    /**
     * 消息包集群路由处理器
     */
    public static final String PACKET_CLUSTER_ROUTER = "PACKET_CLUSTER_ROUTER";

    /**
     * qos 处理器
     */
    public static final String QOS_HANDLER_PRE = "QOS_HANDLER_PRE";

    /**
     * qos 处理器
     */
    public static final String QOS_HANDLER_POST = "QOS_HANDLER_POST";

    /**
     * post 处理器
     */
    public static final String POST_HANDLER = "POST_HANDLER";

    /**
     * 托管 处理器
     */
    public static final String TRUSTEESHIP_HANDLER = "TRUSTEESHIP_HANDLER";

    /**
     * classpath 前缀
     */
    public static final String CLASSPATH_PROTOCOL = "classpath:";

    /**
     * 消息已读状态
     */
    public static final Integer MESSAGE_READ_STATUS = 1;

    /**
     * 消息未读状态
     */
    public static final Integer MESSAGE_UNREAD_STATUS = 0;



    /**
     * 群状态：0-正常
     */
    public static final Integer GROUP_STATUS_0 = 0;

    /**
     * 群状态：1-异常（平台封禁）
     */
    public static final Integer GROUP_STATUS_1 = 1;

    /**
     * 1-是群主
     */
    public static final Integer GROUP_LEADER = 1;

    /**
     * 1-是群主
     */
    public static final Integer NOT_GROUP_LEADER = 0;

    /**
     * 1-是群管理员
     */
    public static final Integer GROUP_MANAGER = 1;

    /**
     * 0-不是群管理员
     */
    public static final Integer NOT_GROUP_MANAGER = 0;


    /**
     * 是群主或管理员
     */
    public static final Integer GROUP_LEADER_OR_MANAGER = 2;


    /**
     * 用户状态：0-正常
     */
    public static final Integer USER_STATUS_0 = 0;

    /**
     * 用户状态：1-异常（平台封禁）
     */
    public static final Integer USER_STATUS_1 = 1;




    /**
     * 屏蔽
     */
    public static final Integer SHIELD = 1;

    /**
     * 未屏蔽
     */
    public static final Integer NOT_SHIELD = 0;

    /**
     * 1-客户端唯一标识（用户）
     */
    public static final Integer USER_TYPE_1 = 1;

    /**
     * 2-群唯一标识
     */
    public static final Integer GROUP_TYPE_2 = 2;

    /**
     * 禁言 0
     */
    public static final Integer MUSHIN = 1;

    /**
     * 未禁言1
     */
    public static final Integer NOT_MUSHIN = 0;

    /**
     * 加入黑名单
     */
    public static final Integer JOIN_BLACKLIST = 1;

    /**
     * 解除黑名单
     */
    public static final Integer NOT_JOIN_BLACKLIST = 0;

    /**
     * 同一设备类型，重复登录获远程登录通知
     */
    public static final String REMOTE_LOGIN_NOTIFICATIONS = "你的ouyunc账号在另一台设备(ip: %s)上登录，你已被迫下线。如果本人不知晓，请立即冻结账号，并及时修改相关密码。";

    /**
     * 好友添加请求应答策略待验证
     */
    public static final Integer FRIEND_ANSWER_POLICY_WAIT_VERIFY = 0;

    /**
     * 好友添加请求应答策略自动同意
     */
    public static final Integer FRIEND_ANSWER_POLICY_AUTO_AGREE = 1;

    /**
     * 群添加请求应答策略待验证
     */
    public static final Integer GROUP_ANSWER_POLICY_WAIT_VERIFY = 0;

    /**
     * 群添加请求应答策略自动同意
     */
    public static final Integer GROUP_ANSWER_POLICY_AUTO_AGREE = 1;



    /**
     * 主动添加群
     */
    public static final Integer GROUP_JOIN_AGREE_REFUSE = 0;


    /**
     * 被动添加群（被邀请）
     */
    public static final Integer GROUP_INVITE_AGREE_REFUSE = 1;

    /**
     * 日志全局traceId
     */
    public static final String LOG_TRACE_ID = "trace-id";

    /**
     * 日志全局跟踪spanId
     */
    public static final String LOG_SPAN_ID = "span-id";

    /**
     * 日志全局跟踪 parentSpanId
     */
    public static final String LOG_PARENT_SPAN_ID = "parent-span-id";

    /**
     * 线程池名称
     */
    public static final String OUYUNC_IM_THREAD_POLL = "ouyunc-im";

    /**
     * 被托管
     */
    public static final Integer TRUSTEESHIP = 1;


    /**
     * 未被托管
     */
    public static final Integer NOT_TRUSTEESHIP = 0;

    /**
     * 是机器人
     */
    public static final Integer ROBOT = 1;


    /**
     * 不是机器人
     */
    public static final Integer NOT_ROBOT = 0;


    /**
     * 消息已撤回
     */
    public static final Integer WITHDRAW = 1;

    /**
     * 消息未撤回
     */
    public static final Integer NOT_WITHDRAW = 0;

    /**
     * mqtt
     */
    public static final String MQTT = "mqtt";

    /**
     * mqtt31
     */
    public static final String MQTT31 = "mqttv3.1";
    /**
     * SEC_WEBSOCKET_PROTOCOL
     */
    public static final String SEC_WEBSOCKET_PROTOCOL = "sec-websocket-protocol";

    /**
     * mqtt 与websocket 协议的编解码
     */
    public static final String MQTT_WEBSOCKET_CODEC = "MQTT_WEBSOCKET_CODEC";


    /**
     * mqtt协议的解解码
     */
    public static final String MQTT_DECODER = "MQTT_DECODER";


    /**
     * mqtt协议的编码解码
     */
    public static final String MQTT_ENCODER = "MQTT_ENCODER";

    /**
     * mqtt协议真正处理逻辑的地方
     */
    public static final String MQTT_SERVER = "MQTT_SERVER";

    /**
     * 定义websocket支持的子协议，如果多个使用英文逗号隔开
     */
    public static final String WEBSOCKET_SUB_PROTOCOLS = "mqtt,mqttv3.1";

    /**
     * 清除之前的会话
     */
    public static final int CLEAN_SESSION = 1;

    /**
     * 不清除之前的会话
     */
    public static final int NOT_CLEAN_SESSION = 0;

    /**
     * 主题过滤器无效
     */
    public static final int MQTT_REASON_CODE_INVALID_TOPIC = 143;
}
