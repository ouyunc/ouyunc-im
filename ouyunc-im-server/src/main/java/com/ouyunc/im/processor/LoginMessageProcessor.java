package com.ouyunc.im.processor;


import com.alibaba.fastjson2.JSON;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.cache.l1.distributed.redis.redisson.RedissonFactory;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.DeviceEnum;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.constant.enums.MessageTypeEnum;
import com.ouyunc.im.constant.enums.NetworkEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.domain.ImApp;
import com.ouyunc.im.encrypt.Encrypt;
import com.ouyunc.im.event.IMOnlineEvent;
import com.ouyunc.im.handler.HeartBeatHandler;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.MqttHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.Target;
import com.ouyunc.im.packet.message.content.LoginContent;
import com.ouyunc.im.packet.message.content.ServerNotifyContent;
import com.ouyunc.im.utils.IdentityUtil;
import com.ouyunc.im.utils.SnowflakeUtil;
import com.ouyunc.im.utils.SystemClock;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.timeout.IdleStateHandler;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;

/**
 * @Author fangzhenxun
 * @Description: 登录消息处理器，这个处理类是第一条连接后发送的第一条消息类型，之后才能发送心跳等业务消息
 **/
public class LoginMessageProcessor extends AbstractMessageProcessor {
    private static Logger log = LoggerFactory.getLogger(LoginMessageProcessor.class);


    @Override
    public MessageTypeEnum messageType() {
        return MessageTypeEnum.IM_LOGIN;
    }

    /**
     * @param loginContent
     * @return boolean
     * @Author fangzhenxun
     * @Description 校验非法参数
     */
    public boolean validate(LoginContent loginContent) {
        if (StringUtils.isEmpty(loginContent.getIdentity()) || StringUtils.isEmpty(loginContent.getAppKey()) || StringUtils.isEmpty(loginContent.getSignature()) || loginContent.getCreateTime() <= 0 || Encrypt.AsymmetricEncrypt.prototype(loginContent.getSignatureAlgorithm()) == null) {
            return false;
        }
        // raw = appkey&identity&createtime_appSecret
        // 通过 appKey 在缓存或数据库中获取账户及权限信息，然后进行计算校验
        ImApp app = DbHelper.getApp(loginContent.getAppKey());
        if (app == null) {
            return false;
        }
        String rawStr = loginContent.getAppKey() + IMConstant.AND + loginContent.getIdentity() + IMConstant.AND + loginContent.getCreateTime() + IMConstant.UNDER_LINE + app.getAppSecret();
        if (!Encrypt.AsymmetricEncrypt.prototype(loginContent.getSignatureAlgorithm()).validate(rawStr, loginContent.getSignature())) {
            return false;
        }
        // 是否开启
        if (IMServerContext.SERVER_CONFIG.isLoginMaxConnectionValidateEnable() && IMConstant.MINUS_ONE.equals(app.getImMaxConnections())) {
            // 做权限校验，比如，同一个appKey 只允许在线10个连接
            Integer connections = IMServerContext.LOGIN_IM_APP_CONNECTIONS_CACHE.getHashAll(CacheConstant.OUYUNC + CacheConstant.APP_KEY + loginContent.getAppKey() + CacheConstant.COLON + CacheConstant.CONNECTIONS).size();
            // 计数从0开始,不能超过最大连接数
            if (++connections >= app.getImMaxConnections()) {
                return false;
            }
        }

        return true;
    }

    /**
     * 登录消息的前置处理
     * 注意：正式中建议使用登录认证
     *
     * @param ctx
     * @param packet
     */
    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 这里判断该消息是否需要
        log.info("正在处理预登录消息...");
        // 取出登录消息
        final Message loginMessage = (Message) packet.getMessage();
        //将消息内容转成message
        LoginContent loginContent = JSON.parseObject(loginMessage.getContent(), LoginContent.class);
        // 做登录参数校验
        //1,进行参数合法校验，校验失败，结束 ；2,进行签名的校验，校验失败，结束，3，进行权限校验，校验失败，结束
        // 根据appKey 获取appSecret 然后拼接
        // 这里加锁的目的是为了，对同一个app下的了连接数保证安全性
        RLock lock = RedissonFactory.INSTANCE.redissonClient().getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP + loginContent.getAppKey());
        try {
            lock.lock();
            if (IMServerContext.SERVER_CONFIG.isLoginValidateEnable() && !validate(loginContent)) {
                log.warn("客户端id: {} 登录参数: {}，校验未通过！", ctx.channel().id().asShortText(), JSON.toJSONString(loginContent));
                ctx.close();
                return;
            }
            final String comboIdentity = IdentityUtil.generalComboIdentity(loginContent.getIdentity(), packet.getDeviceType());
            //如果之前已经登录（重复登录请求），这里判断是否已经登录过,同一个账号在同一个设备不能同时登录
            //1,从分布式缓存取出该登录用户
            LoginUserInfo loginUserInfo = IMServerContext.LOGIN_USER_INFO_CACHE.getHash(CacheConstant.OUYUNC + CacheConstant.APP_KEY + loginContent.getAppKey() + CacheConstant.COLON + CacheConstant.LOGIN + CacheConstant.USER + loginContent.getIdentity(), DeviceEnum.getDeviceNameByValue(packet.getDeviceType()));
            //2,从本地用户注册表中取出该用户的channel
            final ChannelHandlerContext bindCtx = IMServerContext.USER_REGISTER_TABLE.get(comboIdentity);
            // 如果是都不为空是重复登录请求(1，不同的设备远程登录，2，同一设备重复发送登录请求)，向原有的连接发送通知，有其他客户端登录，并将其连接下线
            // 下面如论是否开启支持清除公共注册表的相关信息
            // 构造默认发送的是IM 的消息格式
            Message message = new Message(IMServerContext.SERVER_CONFIG.getIp(), loginContent.getIdentity(), MessageContentEnum.SERVER_NOTIFY_CONTENT.type(), JSON.toJSONString(new ServerNotifyContent(String.format(IMConstant.REMOTE_LOGIN_NOTIFICATIONS, packet.getIp()))), SystemClock.now());
            // 注意： 这里的原来的连接使用的序列化方式，应该是和新连接上的序列化方式一致，这里当成一致，当然不一致也可以做，后面遇到再改造
            Packet notifyPacket = new Packet(packet.getProtocol(), packet.getProtocolVersion(), SnowflakeUtil.nextId(), DeviceEnum.PC_LINUX.getValue(), NetworkEnum.OTHER.getValue(), IMServerContext.SERVER_CONFIG.getIp(), MessageTypeEnum.IM_SERVER_NOTIFY.getValue(), Encrypt.SymmetryEncrypt.NONE.getValue(), packet.getSerializeAlgorithm(), message);
            if (loginUserInfo != null) {
                // 删除原来的redis缓存中的登录信息
                IMServerContext.LOGIN_USER_INFO_CACHE.deleteHash(CacheConstant.OUYUNC + CacheConstant.APP_KEY + loginContent.getAppKey() + CacheConstant.COLON + CacheConstant.LOGIN + CacheConstant.USER + loginContent.getIdentity(), DeviceEnum.getDeviceNameByValue(packet.getDeviceType()));
                // 给原有连接发送通知消息，并将其下线，添加新的连接登录
                if (MessageContentEnum.MQTT.type() == loginMessage.getContentType()) {
                    // 设置消息类型
                    notifyPacket.setMessageType(MessageTypeEnum.MQTT_CONNACK.getValue());
                    // 构造mqtt消息内容，这里原因码使用这个，可根据需求自行修改 CONNECTION_REFUSED_USE_ANOTHER_SERVER 使用另一台服务器，
                    MqttConnAckMessage mqttConnAckMessageContent = (MqttConnAckMessage) MqttMessageFactory.newMessage(
                            new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                            new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_USE_ANOTHER_SERVER, false), null);
                    message.setContentType(MessageContentEnum.MQTT.type());
                    message.setContent(MqttHelper.gson().toJson(mqttConnAckMessageContent));
                    notifyPacket.setMessage(message);
                }
                // 异步发送给已经在线的通知
                MessageHelper.asyncDeliveryMessage(notifyPacket, Target.newBuilder().targetIdentity(loginUserInfo.getIdentity()).targetServerAddress(loginUserInfo.getLoginServerAddress()).deviceEnum(loginUserInfo.getDeviceEnum()).build());
            }
            if (bindCtx != null) {
                IMServerContext.USER_REGISTER_TABLE.delete(comboIdentity);
                bindCtx.close();
            }
            // 绑定信息
            loginUserInfo = UserHelper.bind(loginContent.getAppKey(), loginContent.getIdentity(), packet.getDeviceType(), ctx);
            // 接收端回应登录设备登录成功信息
            // 如果是mqtt的登录消息需要额外发送连接确认给客户端
            if (MessageContentEnum.MQTT.type() == loginMessage.getContentType()) {
                // 判断mqtt 的 cleanSession 是否开启 清除会话，主要是订阅相关数据
                if (IMConstant.CLEAN_SESSION == loginContent.getCleanSession()) {
                    MqttHelper.cleanSession(loginContent);
                }
                // mqtt 保存遗嘱
                if (IMConstant.ENABLE_WILL == loginContent.getEnableWill()) {
                    MqttHelper.saveWillMessage(loginContent);
                }
                // mqtt回应连接端AcK
                // 设置消息类型
                notifyPacket.setMessageType(MessageTypeEnum.MQTT_CONNACK.getValue());
                MqttConnAckMessage okResp = (MqttConnAckMessage) MqttMessageFactory.newMessage(
                        new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                        new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_ACCEPTED, true), null);
                message.setContentType(MessageContentEnum.MQTT.type());
                message.setContent(MqttHelper.gson().toJson(okResp));
                // mqtt 根据cleanSession 处理dup
                if (IMConstant.NOT_CLEAN_SESSION == loginContent.getCleanSession()) {
                    MqttHelper.publishDupMessage(loginContent);
                }
            }
            // 同步发送登录成功消息给客户端
            MessageHelper.syncSendMessage(notifyPacket, Target.newBuilder().targetIdentity(loginUserInfo.getIdentity()).targetServerAddress(loginUserInfo.getLoginServerAddress()).deviceEnum(loginUserInfo.getDeviceEnum()).build());
            // 发送客户端成功登录事件
            IMServerContext.publishEvent(new IMOnlineEvent(loginUserInfo, Clock.systemUTC()), true);
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        // 判断是否加入读写空闲,只要服务端开启支持心跳，才会可能加入心跳处理，这里可以根据自己的协议或业务逻辑进行调整
        if (IMServerContext.SERVER_CONFIG.isHeartBeatEnable()) {
            // 判断是否开启客户端心跳
            ctx.pipeline()
                    // 添加读写空闲处理器， 添加后，下条消息就可以接收心跳消息了
                    .addAfter(IMConstant.CONVERT_2_PACKET, IMConstant.HEART_BEAT_IDLE, new IdleStateHandler(loginContent.getHeartBeatExpireTime() > 0 ? Math.round(loginContent.getHeartBeatExpireTime() * 1.5f) : IMServerContext.SERVER_CONFIG.getHeartBeatTimeout(), 0, 0))
                    // 处理心跳的以及相关逻辑都放在这里处理
                    .addAfter(IMConstant.HEART_BEAT_IDLE, IMConstant.HEART_BEAT_HANDLER, new HeartBeatHandler());
        }

    }


    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        // do nothing
    }
}
