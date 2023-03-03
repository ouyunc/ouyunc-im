package com.ouyunc.im.constant;

/**
 * @Author fangzhenxun
 * @Description: channel 通道相关的常量类
 * @Version V3.0
 **/
public class IMConstant {


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
    public static final String UNDERLINE = "_";

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
    public static final String LOCAL_IP_KEY = "local-ip";

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
    public static final String AUTHENTICATION = "AUTHENTICATION";

    /**
     * 转换为packet处理器
     */
    public static final String CONVERT_2_PACKET = "CONVERT_2_PACKET";

    /**
     * SSL/TLS 处理器
     */
    public static final String SSL = "SSL";

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
     * qos 处理器
     */
    public static final String QOS_HANDLER = "QOS_HANDLER";

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

}
