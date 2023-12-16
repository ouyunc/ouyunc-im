package com.ouyunc.im.helper;

import com.google.common.primitives.Bytes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.*;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.domain.MqttTopic;
import com.ouyunc.im.encrypt.Encrypt;
import com.ouyunc.im.exception.IMException;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.protocol.Protocol;
import com.ouyunc.im.serialize.Serializer;
import com.ouyunc.im.utils.ReaderWriterUtil;
import com.ouyunc.im.utils.SnowflakeUtil;
import com.ouyunc.im.utils.SystemClock;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.mqtt.*;
import io.netty.util.AttributeKey;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Author fangzhenxun
 * @Description mqtt 助手
 */
public class MqttHelper {
    private static Logger log = LoggerFactory.getLogger(MqttHelper.class);

    /**
     * gson
     */
    private static Gson GSON = new GsonBuilder().enableComplexMapKeySerialization() //当Map的key为复杂对象时,需要开启该方法
            .serializeNulls() //当字段值为空或null时，依然对该字段进行转换
//                .excludeFieldsWithoutExposeAnnotation()//打开Export注解，但打开了这个注解,副作用，要转换和不转换都要加注解
            .setDateFormat("yyyy-MM-dd HH:mm:ss")//序列化日期格式  "yyyy-MM-dd"
//                .setPrettyPrinting() //自动格式化换行
            .disableHtmlEscaping() //防止特殊字符出现乱码
            .create();

    /**
     * @param
     * @return com.google.gson.Gson
     * @Author fangzhenxun
     * @Description 获取gson
     */
    public static Gson gson() {
        return GSON;
    }

    /**
     * @param ctx
     * @param mqttMessage
     * @return com.ouyunc.im.packet.Packet
     * @Author fangzhenxun
     * @Description 包装或转换mqtt消息到packet
     */
    public static Packet wrapMqtt2Packet(ChannelHandlerContext ctx, MqttMessage mqttMessage) {
        // 消息解码器出现异常
        if (validateMqttDecoderResultException(ctx, mqttMessage)) {
            return null;
        }
        // 解码成功后，再去转换传递
        if (mqttMessage.decoderResult().isSuccess()) {
            return ReaderWriterUtil.convertOther2Packet(mqttMessage, mqttMessage0 -> {
                String from = null;
                String to = null;
                MqttFixedHeader fixedHeader = mqttMessage0.fixedHeader();
                MqttMessageType mqttMessageType = fixedHeader.messageType();
                AttributeKey<LoginUserInfo> channelTagLoginKey = AttributeKey.valueOf(IMConstant.CHANNEL_TAG_LOGIN);
                LoginUserInfo loginUserInfo = ctx.channel().attr(channelTagLoginKey).get();
                if (loginUserInfo == null) {
                    // 在通过消息内容获取，比如连接，或者携带唯一标识，这里只对连接信息做处理
                    if (MqttMessageType.CONNECT.equals(mqttMessageType)) {
                        MqttConnectPayload payload = (MqttConnectPayload) mqttMessage0.payload();
                        from = payload.clientIdentifier();
                        // 这里要求connect 时 clientId不为空
                        if (StringUtils.isBlank(from)) {
                            MqttConnAckMessage connAckMessage = (MqttConnAckMessage) MqttMessageFactory.newMessage(
                                    new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                                    new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, false), null);
                            ctx.writeAndFlush(connAckMessage);
                            ctx.close();
                        }
                    } else {
                        // @todo 通过其他内容去拿到from的唯一标识， 暂不做实现
                    }
                    if (from == null) {
                        return null;
                    }
                } else {
                    // 一般是已经连接上的非连接命令消息类型，可能会重复连接，需要在后面处理
                    from = loginUserInfo.getIdentity();
                }
                // 这里需要根据不同的主题来匹配对应的唯一标识：主题存储结构  topicId, topic, topicDescription
                // @todo 注意这里的from 从ctx中的属性取，当然也可以每次发消息携带过来 ; to 应该指的是订阅某个topic的客户，把他抽象一个群，topic就是一个群组，订阅该topic就是该群组中的人，可以使用redis 的hash 来存储topic 和 群成员的关系，这里要拿到topic 的所有订阅的成员
                Message message = new Message(from, to, MessageContentEnum.MQTT.type(), GSON.toJson(mqttMessage0), SystemClock.now());
                return new Packet(Protocol.MQTT.getProtocol(), Protocol.MQTT.getVersion(), SnowflakeUtil.nextId(), DeviceEnum.OTHER.getValue(), NetworkEnum.OTHER.getValue(), IMServerContext.SERVER_CONFIG.getIp(), MessageEnum.getMessageEnumByName(String.valueOf(mqttMessageType.value())).getValue(), Encrypt.SymmetryEncrypt.NONE.getValue(), Serializer.PROTO_STUFF.getValue(), message);
            });
        }
        return null;
    }


    /**
     * @param packet
     * @return io.netty.handler.codec.mqtt.MqttMessage
     * @Author fangzhenxun
     * @Description 将packet 转换成mqttMessage
     */
    public static MqttMessage unwrapPacket2Mqtt(Packet packet) {
        if (packet == null) {
            return null;
        }
        MessageEnum prototype = MessageEnum.prototype(packet.getMessageType());
        if (prototype == null) {
            return null;
        }
        String content = ((Message) packet.getMessage()).getContent();
        MqttMessage mqttMessage = GSON.fromJson(content, MqttMessage.class);
        Map<String, Object> variableHeader = (Map<String, Object>) mqttMessage.variableHeader();
        Map<String, Object> payload = (Map<String, Object>) mqttMessage.payload();
        Object mqttVariableHeader = null;
        Object mqttPayload = null;
        switch (prototype) {
            case IM_LOGIN:
            case MQTT_CONNECT:
                // variableHeader
                if (variableHeader != null) {
                    String name = MapUtils.getString(variableHeader, "name");
                    int version = MapUtils.getIntValue(variableHeader, "version");
                    boolean hasUserName = MapUtils.getBooleanValue(variableHeader, "hasUserName");
                    boolean hasPassword = MapUtils.getBooleanValue(variableHeader, "hasPassword");
                    boolean isWillRetain = MapUtils.getBooleanValue(variableHeader, "isWillRetain");
                    int willQos = MapUtils.getIntValue(variableHeader, "willQos");
                    boolean isWillFlag = MapUtils.getBooleanValue(variableHeader, "isWillFlag");
                    boolean isCleanSession = MapUtils.getBooleanValue(variableHeader, "isCleanSession");
                    int keepAliveTimeSeconds = MapUtils.getIntValue(variableHeader, "keepAliveTimeSeconds");
                    Map<String, Object> propertiesMap = (Map<String, Object>) MapUtils.getMap(variableHeader, "properties");
                    MqttProperties properties = null;
                    properties = wrapProperties(properties, propertiesMap);
                    mqttVariableHeader = new MqttConnectVariableHeader(name, version, hasUserName, hasPassword, isWillRetain, willQos, isWillFlag, isCleanSession, keepAliveTimeSeconds, properties);
                }

                // payload
                if (payload != null) {
                    String clientIdentifier = MapUtils.getString(payload, "clientIdentifier");
                    String willTopic = MapUtils.getString(payload, "willTopic");
                    String userName = MapUtils.getString(payload, "userName");
                    Map<String, Object> willPropertiesMap = (Map<String, Object>) MapUtils.getMap(payload, "willProperties");
                    byte[] passwordBytes = null;
                    byte[] willMessageBytes = null;
                    MqttProperties willProperties = null;
                    List<Number> password = (List) MapUtils.getObject(payload, "password");
                    if (CollectionUtils.isNotEmpty(password)) {
                        passwordBytes = Bytes.toArray(password);
                    }
                    List<Number> willMessage = (List) MapUtils.getObject(payload, "willMessage");
                    if (CollectionUtils.isNotEmpty(willMessage)) {
                        willMessageBytes = Bytes.toArray(willMessage);
                    }
                    if (willPropertiesMap != null) {
                        willProperties = wrapProperties(willProperties, willPropertiesMap);
                    }
                    mqttPayload = new MqttConnectPayload(clientIdentifier, willProperties, willTopic, willMessageBytes, userName, passwordBytes);
                }

                break;
            case MQTT_CONNACK:
                break;
            case MQTT_PUBLISH:
                break;
            case MQTT_PUBACK:
                break;
            case MQTT_PUBREC:
                break;
            case MQTT_PUBREL:
                break;
            case MQTT_PUBCOMP:
                break;
            case MQTT_SUBSCRIBE:

                // variableHeader
                if (variableHeader != null) {
                    int messageId = MapUtils.getIntValue(variableHeader, "messageId");
                    Map<String, Object> subscribePropertiesMap = (Map<String, Object>) MapUtils.getMap(variableHeader, "properties");
                    MqttProperties subscribeProperties = null;
                    subscribeProperties = wrapProperties(subscribeProperties, subscribePropertiesMap);
                    mqttVariableHeader = new MqttMessageIdAndPropertiesVariableHeader(messageId, subscribeProperties);
                }

                // payload
                if (payload != null) {
                    List<Map<String, Object>> topicSubscriptionsMapList = (List) MapUtils.getObject(payload, "topicSubscriptions");
                    List<MqttTopicSubscription> topicSubscriptions = new ArrayList<>();
                    if (CollectionUtils.isNotEmpty(topicSubscriptionsMapList)) {
                        // 遍历订阅的主题
                        topicSubscriptionsMapList.forEach(topicSubscriptionsMap -> {
                            String topicFilter = MapUtils.getString(topicSubscriptionsMap, "topicFilter");
                            Map<String, Object> optionMap = (Map<String, Object>) MapUtils.getMap(topicSubscriptionsMap, "option");
                            int qos = MapUtils.getIntValue(optionMap, "qos");
                            boolean noLocal = MapUtils.getBooleanValue(optionMap, "noLocal");
                            boolean retainAsPublished = MapUtils.getBooleanValue(optionMap, "retainAsPublished");
                            int retainHandling = MapUtils.getIntValue(optionMap, "retainHandling");
                            topicSubscriptions.add(new MqttTopicSubscription(topicFilter, new MqttSubscriptionOption(MqttQoS.valueOf(qos), noLocal, retainAsPublished, MqttSubscriptionOption.RetainedHandlingPolicy.valueOf(retainHandling))));
                        });
                    }
                    mqttPayload = new MqttSubscribePayload(topicSubscriptions);
                }

                break;
            case MQTT_SUBACK:
                break;
            case MQTT_UNSUBSCRIBE:
                break;
            case MQTT_UNSUBACK:
                break;
            case IM_PING_PONG:
            case MQTT_PINGREQ:
                break;
            case MQTT_PINGRESP:
                break;
            case MQTT_DISCONNECT:
                if (variableHeader != null) {
                    byte reasonCode = MapUtils.getByteValue(variableHeader, "reasonCode");
                    Map<String, Object> disConnectPropertiesMap = (Map<String, Object>) MapUtils.getMap(variableHeader, "properties");
                    MqttProperties disConnectProperties = null;
                    disConnectProperties = wrapProperties(disConnectProperties, disConnectPropertiesMap);
                    mqttVariableHeader = new MqttReasonCodeAndPropertiesVariableHeader(reasonCode, disConnectProperties);
                }
                break;
            case MQTT_AUTH:
                break;
            default:
                throw new IMException("找不到对应的mqtt处理类型：" + prototype);

        }
        return new MqttMessage(mqttMessage.fixedHeader(), mqttVariableHeader, mqttPayload, DecoderResult.SUCCESS);
    }


    /**
     * @param mqttMessage
     * @return void
     * @Author fangzhenxun
     * @Description 校验 mqtt 编码是否异常，根据不同的消息类型来进行处理，@todo 需要根据不同的类型做处理
     */
    private static boolean validateMqttDecoderResultException(ChannelHandlerContext ctx, MqttMessage mqttMessage) {
        if (mqttMessage.decoderResult().isFailure()) {
            MessageEnum prototype = MessageEnum.prototype((byte) mqttMessage.fixedHeader().messageType().value());
            switch (prototype) {
                case MQTT_CONNECT:
                    // 在这里就要判断是什么类型的错误
                    Throwable cause = mqttMessage.decoderResult().cause();
                    log.error("mqtt 协议解码失败, 原因：{}", cause.getMessage());

                    if (cause instanceof MqttUnacceptableProtocolVersionException) {
                        // 非法协议或版本
                        MqttConnAckMessage connAckMessage = (MqttConnAckMessage) MqttMessageFactory.newMessage(new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0), new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION, false), null);
                        ctx.writeAndFlush(connAckMessage);
                    } else if (cause instanceof MqttIdentifierRejectedException) {
                        // 非法clientId
                        MqttConnAckMessage connAckMessage = (MqttConnAckMessage) MqttMessageFactory.newMessage(new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0), new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, false), null);
                        ctx.writeAndFlush(connAckMessage);
                    }
                    ctx.close();
                    break;
                case MQTT_CONNACK:
                    break;
                case MQTT_PUBLISH:
                    break;
                case MQTT_PUBACK:
                    break;
                case MQTT_PUBREC:
                    break;
                case MQTT_PUBREL:
                    break;
                case MQTT_PUBCOMP:
                    break;
                case MQTT_SUBSCRIBE:
                    break;
                case MQTT_SUBACK:
                    break;
                case MQTT_UNSUBSCRIBE:
                    break;
                case MQTT_UNSUBACK:
                    break;
                case MQTT_PINGREQ:
                    break;
                case MQTT_PINGRESP:
                    break;
                case MQTT_DISCONNECT:
                    break;
                case MQTT_AUTH:
                    break;
                default:
                    throw new IMException("找不到对应的mqtt处理类型：" + prototype);

            }
            return true;
        }
        return false;
    }


    /**
     * @param properties
     * @param propertiesMap
     * @return io.netty.handler.codec.mqtt.MqttProperties
     * @Author fangzhenxun
     * @Description 参考 mqtt5.0 的协议 properties 属性标识对应的类型
     */
    private static MqttProperties wrapProperties(MqttProperties properties, Map<String, Object> propertiesMap) {
        if (propertiesMap != null) {
            boolean canModify = MapUtils.getBooleanValue(propertiesMap, "canModify");
            if (properties == null) {
                if (canModify) {
                    properties = new MqttProperties();
                } else {
                    return properties = MqttProperties.NO_PROPERTIES;
                }
            }
            Map<String, Object> propsMap = (Map<String, Object>) MapUtils.getMap(propertiesMap, "props");
            if (propsMap != null) {
                for (Map.Entry<String, Object> entry : propsMap.entrySet()) {
                    doWrapProperties(properties, (Map<String, Object>) entry.getValue());
                }
            }
            List<Map<String, Object>> userPropertiesList = (List<Map<String, Object>>) MapUtils.getObject(propertiesMap, "userProperties");
            if (userPropertiesList != null) {
                for (Map<String, Object> userPropertiesMap : userPropertiesList) {
                    doWrapProperties(properties, userPropertiesMap);
                }
            }
            List<Map<String, Object>> subscriptionIdsList = (List<Map<String, Object>>) MapUtils.getObject(propertiesMap, "subscriptionIds");
            if (subscriptionIdsList != null) {
                for (Map<String, Object> subscriptionIdMap : subscriptionIdsList) {
                    doWrapProperties(properties, subscriptionIdMap);
                }
            }

        }
        return properties;
    }

    /**
     * @param properties
     * @param valueMap
     * @return void
     * @Author fangzhenxun
     * @Description 参考mqtt5.0 进行包装属性
     */
    private static void doWrapProperties(MqttProperties properties, Map<String, Object> valueMap) {
        if (valueMap != null) {
            int propertyId = MapUtils.getIntValue(valueMap, "propertyId");
            Object value = valueMap.get("value");
            if (value == null) {
                return;
            }
            doProperties(properties, propertyId, value);
        }
    }


    /**
     * @param properties
     * @param propertyId
     * @param value
     * @return void
     * @Author fangzhenxun
     * @Description 根据不同的属性id进行类型选择
     */
    private static <T> void doProperties(MqttProperties properties, int propertyId, T value) {
        MqttProperties.MqttPropertyType propertyType = MqttProperties.MqttPropertyType.valueOf(propertyId);
        switch (propertyType) {
            // single byte properties
            case PAYLOAD_FORMAT_INDICATOR:
            case REQUEST_PROBLEM_INFORMATION:
            case REQUEST_RESPONSE_INFORMATION:
            case MAXIMUM_QOS:
            case RETAIN_AVAILABLE:
            case WILDCARD_SUBSCRIPTION_AVAILABLE:
            case SUBSCRIPTION_IDENTIFIER_AVAILABLE:
            case SHARED_SUBSCRIPTION_AVAILABLE:

                // two bytes properties
            case SERVER_KEEP_ALIVE:
            case RECEIVE_MAXIMUM:
            case TOPIC_ALIAS_MAXIMUM:
            case TOPIC_ALIAS:

                // four bytes properties
            case PUBLICATION_EXPIRY_INTERVAL:
            case SESSION_EXPIRY_INTERVAL:
            case WILL_DELAY_INTERVAL:
            case MAXIMUM_PACKET_SIZE:

                // Variable Byte Integer
            case SUBSCRIPTION_IDENTIFIER:
                properties.add(new MqttProperties.IntegerProperty(propertyId, ((Number) value).intValue()));
                break;


            // UTF-8 Encoded String properties
            case CONTENT_TYPE:
            case RESPONSE_TOPIC:
            case ASSIGNED_CLIENT_IDENTIFIER:
            case AUTHENTICATION_METHOD:
            case RESPONSE_INFORMATION:
            case SERVER_REFERENCE:
            case REASON_STRING:
                properties.add(new MqttProperties.StringProperty(propertyId, String.valueOf(value)));
                break;
            case USER_PROPERTY:
                String keyUser = MapUtils.getString((Map) value, "key");
                String valueUser = MapUtils.getString((Map) value, "value");
                properties.add(new MqttProperties.UserProperty(keyUser, valueUser));
                break;

            // Binary Data
            case CORRELATION_DATA:
            case AUTHENTICATION_DATA:
                byte[] bytes = Bytes.toArray((List<Number>) value);
                properties.add(new MqttProperties.BinaryProperty(propertyId, bytes));
                break;
            default:
                //shouldn't reach here
                throw new DecoderException("Unknown property type: " + propertyType);
        }
    }



    /**
     * @param
     * @return io.netty.channel.pool.ChannelPool
     * @Author fangzhenxun
     * @Description 匹配topic ,根据不同的策略来实现，如果针对大数据量可以使用二级缓存等优化方式
     */
    public static List<MqttTopic> routeTopic(String topic) {
        return null;
    }


    /**
     * @Author fangzhenxun
     * @Description 合法返回true， 否则返回false
     * @param mqttTopicSubscriptions
     * @return boolean
     */
    public static boolean validateTopic(List<MqttTopicSubscription> mqttTopicSubscriptions) {
        return true;
    }
}