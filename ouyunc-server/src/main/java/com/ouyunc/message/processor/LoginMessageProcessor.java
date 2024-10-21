package com.ouyunc.message.processor;


import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.packet.message.content.ServerNotifyContent;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.SnowflakeUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.listener.event.ClientLoginEvent;
import com.ouyunc.core.listener.event.ClientLogoutEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.handler.HeartBeatHandler;
import com.ouyunc.message.handler.LoginKeepAliveHandler;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateHandler;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @Author fzx
 * @Description: 登录消息处理器，这个处理类是第一条连接后发送的第一条消息类型，之后才能发送心跳等业务消息
 **/
public class LoginMessageProcessor extends AbstractMessageProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(LoginMessageProcessor.class);


    /***
     * @author fzx
     * @description 消息前置处理，做登录业务逻辑
     */
    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 这里判断该消息是否需要
        log.info("正在处理预登录消息...");
        // 构造默认发送的是IM 的消息格式
        long loginTimestamp = TimeUtil.currentTimeMillis();
        // 取出登录消息
        Message loginMessage = packet.getMessage();
        //将消息内容转成message
        LoginContent loginContent = JSON.parseObject(loginMessage.getContent(), LoginContent.class);
        // 设置设备类型
        loginContent.setDeviceType(MessageServerContext.deviceTypeCache.get(packet.getDeviceType()));
        // 做登录参数校验
        //1,进行参数合法校验，校验失败，结束 ；2,进行签名的校验，校验失败，结束，3，进行权限校验，校验失败，结束
        // 根据appKey 获取appSecret 然后拼接
        if (!validate(loginContent)) {
            log.warn("客户端id: {} 登录参数: {}，校验未通过！", ctx.channel().id().asShortText(), Serializer.JSON.serializeToString(loginContent));
            ctx.close();
            return;
        }
        DeviceType deviceType = MessageServerContext.deviceTypeCache.get(packet.getDeviceType());
        String comboIdentity = IdentityUtil.generalComboIdentity(loginContent.getAppKey(), loginContent.getIdentity(), deviceType.getDeviceTypeName());
        String clientLoginCacheKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + loginContent.getAppKey() + CacheConstant.COLON + CacheConstant.LOGIN + CacheConstant.USER + comboIdentity;
        //如果之前已经登录（重复登录请求），这里判断是否已经登录过,同一个账号在同一个设备不能同时登录
        //1,从分布式缓存取出该登录用户
        LoginClientInfo cacheLoginClientInfo = MessageServerContext.remoteLoginClientInfoCache.get(clientLoginCacheKey);
        //2,从本地用户注册表中取出该用户的channel
        ChannelHandlerContext bindCtx = MessageServerContext.localClientRegisterTable.get(comboIdentity);
        // 重复登录请求(1，不同的设备远程登录，2，同一设备重复发送登录请求)，向原有的连接发送通知，有其他客户端登录，并将其连接下线
        // 下面如论是否开启支持清除公共注册表的相关信息
        Message message = new Message(MessageServerContext.serverProperties().getIp(), loginContent.getIdentity(), WsMessageContentTypeEnum.SERVER_NOTIFY_CONTENT.getType(), Serializer.JSON.serializeToString(new ServerNotifyContent(String.format(MessageConstant.REMOTE_LOGIN_NOTIFICATIONS, loginMessage.getMetadata().getClientIp()))), loginTimestamp);
        // 注意： 这里的原来的连接使用的序列化方式，应该是和新连接上的序列化方式一致，这里当成一致，当然不一致也可以做，后面遇到再改造
        Packet notifyPacket = new Packet(packet.getProtocol(), packet.getProtocolVersion(), SnowflakeUtil.nextId(), DeviceTypeEnum.PC.getValue(), NetworkEnum.OTHER.getValue(), WsMessageTypeEnum.SERVER_NOTIFY.getType(), packet.getEncryptType(), packet.getSerializeAlgorithm(), message);
        if (cacheLoginClientInfo != null) {
            // 给原有连接发送通知消息，并将其下线，添加新的连接登录,覆盖之前的登录信息
            // 异步发送给已经在线的通知
            MessageHelper.asyncSendMessage(notifyPacket, Target.newBuilder().targetIdentity(cacheLoginClientInfo.getIdentity()).targetServerAddress(cacheLoginClientInfo.getLoginServerAddress()).deviceType(deviceType).build());
        }
        // 如果还在当前服务登录的话，先关闭之前的连接(这里没有强制去通知让原来的连接进行跨服务下线，只是通过心跳让其自动感知下线)， 如果不在该服务器再次登录，也是需要关闭之前的channel,否则，如果当前登录绑定了信息，后面另外的channel 在关闭然后触发关闭事件，导致删除失败就会把缓存的登录信息给删掉
        if (bindCtx != null) {
            // 如果之前有绑定信息，且不为空，这里会触发close 监听事件，进而会删除本地缓存和远端缓存，注意这里是异步执行，可能会影响绑定的信息
            bindCtx.close();
        }
        // 绑定信息
        cacheLoginClientInfo = ClientHelper.bind(ctx, loginContent, loginTimestamp);
        // 添加channel 关闭后释放资源的钩子, 该逻辑在DefaultSocketChannelInitializer 中进行调用
        Consumer<Channel> channelCloseHook = channel -> {
            //1,从channel中的attrMap取出相关属性
            final LoginClientInfo closingLocalloginClientInfo = ChannelAttrUtil.getChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
            if (closingLocalloginClientInfo != null) {
                // 这里不进行判空了，到这里肯定不为空（登录信息里面一定要有登录设备的类型）
                String clientLoginDeviceName = closingLocalloginClientInfo.getDeviceType().getDeviceTypeName();
                String closingComboIdentity = IdentityUtil.generalComboIdentity(closingLocalloginClientInfo.getAppKey(), closingLocalloginClientInfo.getIdentity(), clientLoginDeviceName);
                // 登录信息一致,才进行解绑，删除缓存信息
                MessageServerContext.localClientRegisterTable.delete(closingComboIdentity);
                String loginClientInfoCacheKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + closingLocalloginClientInfo.getAppKey() + CacheConstant.COLON + CacheConstant.LOGIN + CacheConstant.USER + closingComboIdentity;
                // 获取分布式锁, 这里使用锁的目的，可以参考登录处理器的分布式锁，防止重复解绑 LoginMessageProcessor
                RLock lock = MessageServerContext.redissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + closingLocalloginClientInfo.getAppKey() + CacheConstant.COLON + closingComboIdentity);
                try {
                    if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                        LoginClientInfo closingRemoteLoginClientInfo = MessageServerContext.remoteLoginClientInfoCache.get(loginClientInfoCacheKey);
                        // 这里比较两个登录服务器地址是否一致的目的是因为，无论集群还是单服务 在ctx异步关闭时,有可能存在关闭的执行顺序比绑定客户端的方法执行的慢，导致缓存被覆盖，结果又给删除了缓存信息，导致数据错乱。
                        if (closingRemoteLoginClientInfo != null && closingLocalloginClientInfo.getLoginServerAddress().equals(closingRemoteLoginClientInfo.getLoginServerAddress()) && closingRemoteLoginClientInfo.getLastLoginTime() == closingLocalloginClientInfo.getLastLoginTime()) {
                            // 缓存中有没有登录信息都进行删除下
                            MessageServerContext.remoteLoginClientInfoCache.delete(loginClientInfoCacheKey);
                        }else {
                            log.warn("客户端: {} 解绑登录信息失败,原因：缓存中不存在登录信息或登录地址不匹配", closingLocalloginClientInfo);
                        }
                    }else {
                        log.error("客户端: {} 绑定登录信息失败,原因：获取分布式锁失败", closingLocalloginClientInfo);
                    }
                } catch (Exception e) {
                    log.error("客户端: {} 绑定登录信息失败,原因：{}", closingLocalloginClientInfo, e.getMessage());
                    throw new MessageException(e);
                } finally {
                    if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
                // 发送客户端离线事件， 可以处理发送遗嘱等客户端关闭后的操作逻辑
                MessageServerContext.publishEvent(new ClientLogoutEvent(closingLocalloginClientInfo), true);
            }
        };
        // 设置channel 关闭后的回调
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_CHANNEL_CLOSE_HOOK, channelCloseHook);

        // 判断是否加入读写空闲,只要服务端开启支持心跳，才会可能加入心跳处理，这里可以根据自己的协议或业务逻辑进行调整
        Integer heartbeatExpireTime = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_HEARTBEAT_TIMEOUT);
        if (MessageServerContext.serverProperties().isClientHeartBeatEnable() && heartbeatExpireTime != null) {
            // 判断是否开启客户端心跳
            ctx.pipeline()
                    // 客户端登录保活处理器
                    .addAfter(MessageConstant.CONVERT_2_PACKET_HANDLER, MessageConstant.CLIENT_LOGIN_KEEP_ALIVE_HANDLER, new LoginKeepAliveHandler())
                    // 添加读写空闲处理器， 添加后，下条消息就可以接收心跳消息了
                    .addAfter(MessageConstant.CLIENT_LOGIN_KEEP_ALIVE_HANDLER, MessageConstant.HEART_BEAT_IDLE_HANDLER, new IdleStateHandler(heartbeatExpireTime, MessageConstant.ZERO, MessageConstant.ZERO))
                    // 处理心跳的以及相关逻辑都放在这里处理
                    .addAfter(MessageConstant.HEART_BEAT_IDLE_HANDLER, MessageConstant.HEART_BEAT_HANDLER, new HeartBeatHandler());
        }
        // 接收端回应登录设备登录成功信息
        // 同步发送登录成功消息给客户端
        message.setContentType(WsMessageContentTypeEnum.LOGIN_RESPONSE_SUCCESS_CONTENT.getType());
        message.setContent(null);
        MessageHelper.syncSendMessage(notifyPacket, Target.newBuilder().targetIdentity(cacheLoginClientInfo.getIdentity()).targetServerAddress(cacheLoginClientInfo.getLoginServerAddress()).deviceType(deviceType).build());
        // 发送客户端成功登录事件
        MessageServerContext.publishEvent(new ClientLoginEvent(cacheLoginClientInfo, ctx, loginTimestamp), true);
    }

    /***
     * @author fzx
     * @description 业务处理，登录消息不需要做任何处理
     */
    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        // do nothing
    }


    /***
     * @author fzx
     * @description 校验登录信息
     */
    public boolean validate(LoginContent loginContent) {
        return true;
    }


    @Override
    public MessageType type() {
        return WsMessageTypeEnum.LOGIN;
    }
}
