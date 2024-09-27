package com.ouyunc.base.constant;

/**
 * @Author fzx
 * @Description: 常量类
 **/
public class MessageConstant {

    /**
     * 0
     */
    public static final Integer ZERO = 0;

    /**
     * -1
     */
    public static final Integer MINUS_ONE = -1;

    /**
     * 1
     */
    public static final Integer ONE = 1;

    /**
     * 100
     */
    public static final Integer ONE_HUNDRED = 100;

    /**
     * 锁等待时间 5
     */
    public static final long LOCK_WAIT_TIME = 5;

    /**
     * 锁持有时间 30
     */
    public static final long LOCK_LEASE_TIME = 5;

    /**
     * 自定义协议的魔数,十进制102
     */
    public static final byte PACKET_MAGIC = 0x66;

    /**
     *urf-8
     */
    public static final String UTF_8 = "UTF-8";

    /**
     * 冒号分割符
     */
    public static final String COLON_SPLIT = ":";



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
     * channel 的haproxy protocol 协议标签存放代理后的真实客户端的代理信息  HAProxyMessage
     */
    public static final String CHANNEL_ATTR_KEY_TAG_CLIENT_REAL_IP = "CHANNEL_ATTR_KEY_TAG_HAPROXY_PROTOCOL";

    /**
     * channel 的登录标签，存放的是LoginUserInfo对象
     */
    public static final String CHANNEL_ATTR_KEY_TAG_POOL = "CHANNEL_TAG_POOL";


    /**
     * channel 的登录标签，存放的是LoginUserInfo对象
     */
    public static final String CHANNEL_ATTR_KEY_TAG_LOGIN = "CHANNEL_TAG_LOGIN";


    /**
     * channel 客户端读超时的次数标签
     */
    public static final String CHANNEL_ATTR_KEY_TAG_READ_TIMEOUT_TIMES = "CHANNEL_TAG_CLIENT_READ_TIMEOUT_TIMES";




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
     * 心跳空闲处理器
     */
    public static final String HEART_BEAT_IDLE_HANDLER = "HEART_BEAT_IDLE_HANDLER";

    /**
     * 心跳处理器
     */
    public static final String HEART_BEAT_HANDLER = "HEART_BEAT_HANDLER";


    /**
     * 监控 处理器
     */
    public static final String MONITOR_HANDLER = "MONITOR_HANDLER";
    /**
     * 统一登录处理器
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
    public static final String MQTT31 = "mqttv3.1";


    /**
     * LengthFieldBasedFrameDecoder 最大长度
     */
    public static final int MAX_FRAME_LENGTH = Integer.MAX_VALUE;
    /**
     * LengthFieldBasedFrameDecoder 内容长度字段所占的字节数
     */
    public static final int LENGTH_FIELD_LENGTH = 4;

    /**
     * |    1    |     1   |    1    |     8    |    1     |    1      |    1     |     1     |     1     |    4     |      n    |
     * +---------+---------+---------+----------+----------+----------+-----------+----------+-----------+-----------+----------+-
     * |         |         |         |          |          |          |           |          |           |           |          |
     * |  魔数    |  协议类型| 协议版本  | 协议包id  | 设备类型  | 网络类型   | 加密算法   | 序列化算法 |  消息类型   | 消息长度   |  消息体   |
     * |         |         |         |          |          |          |           |          |           |           |          |
     * +---------+---------+---------+----------+----------+----------+-----------+----------+-----------+-----------+----------+-
     * 参数含义及如何设置： 可参看 https://blog.csdn.net/hxj413977035/article/details/121633308
     */

    /**
     * LengthFieldBasedFrameDecoder 消息长度字段的 偏移字节，这里是固定协议头大小（16字节）
     */
    public static final int LENGTH_FIELD_OFFSET = 16;
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
     * 同一设备类型，重复登录获远程登录通知
     */
    public static final String REMOTE_LOGIN_NOTIFICATIONS = "你的ouyunc账号在另一台设备(ip: %s)上登录，你已被迫下线。如果本人不知晓，请立即冻结账号，并及时修改相关密码。";

}
