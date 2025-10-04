package com.ouyunc.message.handler;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
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
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.ClientLoginEvent;
import com.ouyunc.core.listener.event.ClientLogoutEvent;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.validator.AppKeyValidator;
import com.ouyunc.message.validator.DeviceValidator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.ScheduledFuture;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @Author fzx
 * @Description: 登录认证处理器
 **/
public class AuthenticationHandler extends SimpleChannelInboundHandler<Packet> {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationHandler.class);


    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 登录逻辑处理
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        // 在这里做一次设备的登录支持校验，如果不想在这校验可以下放到processor中来根据不同的校验器做校验
        LoginContent loginInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        // 登录消息
        if (MessageTypeEnum.LOGIN.getType().equals(packet.getMessageType())) {
            if (loginInfo != null) {
                log.warn("重复登录, 正在关闭连接!");
                ctx.close();
                return;
            }
            doLogin(ctx, packet);
            return;
        }
        // 非登录消息，已经登录放行
        if (loginInfo == null) {
            log.warn("请先登录!");
            ctx.close();
            return;
        }
        ctx.fireChannelRead(packet);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ScheduledFuture<?> timeoutFuture = ctx.executor().schedule(() -> {
            LoginContent loginInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
            if (loginInfo == null) {
                log.error("登录超时, 在规定时间：{} s 内未进行登录，现进行关闭连接: {}!", MessageServerContext.serverProperties().getServerLoginTimeout(), ctx.channel().id().asShortText());
                ctx.close();
            }
        }, MessageServerContext.serverProperties().getServerLoginTimeout(), TimeUnit.SECONDS);
        ctx.channel().attr(AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_LOGIN_TIMEOUT_SCHEDULED_FUTURE)).set(timeoutFuture);
        super.channelActive(ctx);
    }


    /**
     * 取消登录超时定时任务
     * @param ctx
     * @return boolean
     */
    private boolean cancelTimeoutFuture(ChannelHandlerContext ctx) {
        boolean cancel = false;
        AttributeKey<ScheduledFuture<?>> attributeKey = AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_LOGIN_TIMEOUT_SCHEDULED_FUTURE);
        ScheduledFuture<?> timeoutFuture = ctx.channel().attr(attributeKey).get();
        if (timeoutFuture != null) {
            cancel = timeoutFuture.cancel(true);
            ctx.channel().attr(AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_LOGIN_TIMEOUT_SCHEDULED_FUTURE)).set(null);
        }
        return cancel;
    }

    /**
     * 登录
     * @param ctx
     * @param packet
     */
    private void doLogin(ChannelHandlerContext ctx, Packet packet) {
        // 构造默认发送的是IM 的消息格式
        long loginTimestamp = TimeUtil.currentTimeMillis();
        // 取出登录消息
        Message loginMessage = packet.getMessage();
        //将消息内容转成message
        LoginContent loginContent = JSON.parseObject(loginMessage.getContent(), LoginContent.class);
        // 设置设备类型
        DeviceType deviceType = MessageServerContext.deviceType(loginContent.getAppKey(), packet.getDeviceType());
        // 做登录参数校验
        //1,进行参数合法校验，校验失败，结束 ；2,进行签名的校验，校验失败，结束，3，进行权限校验，校验失败，结束
        // 根据appKey 获取appSecret 然后拼接
        if (AppKeyValidator.INSTANCE.negate().verify(loginContent.getAppKey(), ctx) || DeviceValidator.INSTANCE.negate().verify(packet, ctx) || !validate(loginContent)) {
            log.warn("客户端id: {} 登录参数: {}，校验未通过！", ctx.channel().id().asShortText(), Serializer.JSON.serializeToString(loginContent));
            MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.LOGIN_VERIFY_ERROR, "登录校验未通过", packet), true);
            ctx.close();
            return;
        }
        String comboIdentity = IdentityUtil.generalComboIdentity(loginContent.getAppKey(), loginContent.getIdentity(), deviceType.getDeviceTypeName());
        //如果之前已经登录（重复登录请求），这里判断是否已经登录过,同一个账号在同一个设备不能同时登录
        //1,从分布式缓存取出该登录用户
        LoginClientInfo cacheLoginClientInfo = MessageServerContext.remoteLoginClientInfoCache.get(CacheConstant.buildLoginCacheKey(loginContent.getAppKey(), comboIdentity));
        //2,从本地用户注册表中取出该用户的channel
        ChannelHandlerContext bindCtx = MessageServerContext.localLoginClientRegisterTable.get(comboIdentity);
        // 重复登录请求(1，不同的设备远程登录，2，同一设备重复发送登录请求)，向原有的连接发送通知，有其他客户端登录，并将其连接下线
        // 下面如论是否开启支持清除公共注册表的相关信息
        Message message = new Message(null, loginContent.getIdentity(), MessageContentTypeEnum.TEXT_CONTENT.getType(), Serializer.JSON.serializeToString(new ServerNotifyContent(String.format(MessageConstant.REMOTE_LOGIN_NOTIFICATIONS, loginMessage.getMetadata().getClientIp()))), loginTimestamp, loginMessage.getMetadata());
        // 注意： 这里的原来的连接使用的序列化方式，应该是和新连接上的序列化方式一致，这里当成一致，当然不一致也可以做，后面遇到再改造
        Packet notifyPacket = new Packet(packet.getProtocol(), packet.getProtocolVersion(), SnowflakeUtil.nextId(), DeviceTypeEnum.PC.getValue(), NetworkEnum.OTHER.getValue(), packet.getEncryptType(), packet.getSerializeAlgorithm(), MessageTypeEnum.SERVER_NOTIFY.getType(), message);
        // 记录设备号如果是同一个设备则不发送，否则发送通知
        if (cacheLoginClientInfo != null && (StringUtils.isBlank(cacheLoginClientInfo.getSn()) || !cacheLoginClientInfo.getSn().equals(loginContent.getSn()))) {
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
        ClientHelper.bind(ctx, cacheLoginClientInfo = new LoginClientInfo(MessageContext.messageProperties.getLocalServerAddress(), OnlineEnum.ONLINE, null, ClientHelper.calculateClientLoginExpireTime(loginContent.getHeartBeatExpireTime()), ClientHelper.calculateClientHeartBeatTimeout(loginContent.getHeartBeatExpireTime()), loginTimestamp, loginContent.getAppKey(), loginContent.getIdentity(), deviceType, loginContent.getSupportDeviceTypes(), loginContent.getSn(), loginContent.getSignature(), loginContent.getSignatureAlgorithm(), loginContent.getHeartBeatExpireTime(), loginTimestamp));

        // 添加channel 关闭后释放资源的钩子, 该逻辑在DefaultSocketChannelInitializer 中进行调用
        Consumer<Channel> channelCloseHook = channel -> {
            //1,从channel中的attrMap取出相关属性
            final LoginClientInfo closingLocalloginClientInfo = ChannelAttrUtil.getChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
            if (closingLocalloginClientInfo != null) {
                // 这里不进行判空了，到这里肯定不为空（登录信息里面一定要有登录设备的类型）
                String clientLoginDeviceName = closingLocalloginClientInfo.getDeviceType().getDeviceTypeName();
                String closingComboIdentity = IdentityUtil.generalComboIdentity(closingLocalloginClientInfo.getAppKey(), closingLocalloginClientInfo.getIdentity(), clientLoginDeviceName);
                // 登录信息一致,才进行解绑，删除缓存信息
                MessageServerContext.localLoginClientRegisterTable.delete(closingComboIdentity);
                String loginClientInfoCacheKey = CacheConstant.buildLoginCacheKey(closingLocalloginClientInfo.getAppKey(), closingComboIdentity);
                // 获取分布式锁, 这里使用锁的目的，可以参考登录处理器的分布式锁，防止重复解绑 LoginMessageProcessor
                RLock lock = MessageServerContext.redissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + closingLocalloginClientInfo.getAppKey() + CacheConstant.COLON + closingComboIdentity);
                try {
                    if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                        LoginClientInfo closingRemoteLoginClientInfo = MessageServerContext.remoteLoginClientInfoCache.get(loginClientInfoCacheKey);
                        // 这里比较两个登录服务器地址是否一致的目的是因为，无论集群还是单服务 在ctx异步关闭时,有可能存在关闭的执行顺序比绑定客户端的方法执行的慢，导致缓存被覆盖，结果又给删除了缓存信息，导致数据错乱。
                        if (closingRemoteLoginClientInfo != null && closingLocalloginClientInfo.getLoginServerAddress().equals(closingRemoteLoginClientInfo.getLoginServerAddress()) && closingRemoteLoginClientInfo.getLastLoginTime() == closingLocalloginClientInfo.getLastLoginTime()) {
                            // 缓存中有没有登录信息都进行删除下
                            // 删除appKey 下的连接统计信息
                            RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();
                            redisTemplate.executePipelined(new SessionCallback<>() {
                                @SuppressWarnings("unchecked")
                                @Override
                                public <K, V> Object execute(@NotNull RedisOperations<K, V> operations) throws DataAccessException {
                                    // 删除登录信息
                                    operations.delete((K) loginClientInfoCacheKey);
                                    // 删除appKey 下的连接统计信息
                                    operations.opsForZSet().remove((K) (CacheConstant.buildConnectionsCacheKey(closingLocalloginClientInfo.getAppKey())), closingComboIdentity);
                                    return null;
                                }
                            });
                        } else {
                            log.warn("客户端: {} 解绑登录信息失败,原因：缓存中不存在登录信息或登录地址不匹配", closingLocalloginClientInfo);
                            MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.UN_BIND_ERROR, "客户端解绑登录信息失败！缓存中不存在登录信息或登录地址不匹配", packet));
                        }
                    } else {
                        log.error("客户端: {} 解绑登录信息失败,原因：获取分布式锁失败", closingLocalloginClientInfo);
                        MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.UN_BIND_ERROR, "客户端解绑登录信息失败！获取分布式锁失败", packet));
                    }
                } catch (Exception e) {
                    log.error("客户端: {} 解绑登录信息失败,原因：{}", closingLocalloginClientInfo, e.getMessage());
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.UN_BIND_ERROR, "客户端解绑登录信息失败！" + e.getMessage(), packet));
                    throw new MessageException(e);
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
                // 发送客户端离线事件， 可以处理发送遗嘱等客户端关闭后的操作逻辑
                MessageServerContext.publishEvent(new ClientLogoutEvent(closingLocalloginClientInfo), true);
            }
        };
        // 设置channel 关闭后的回调
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_CHANNEL_CLOSE_HOOK, channelCloseHook);

        // 判断是否加入读写空闲,只要服务端开启支持心跳，才会可能加入心跳处理，这里可以根据自己的协议或业务逻辑进行调整，为什么要放到登录处理器后面呢？因为有些业务可能不需要心跳，比如信令服务等
        Integer heartbeatExpireTime = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_HEARTBEAT_TIMEOUT);
        if (MessageServerContext.serverProperties().isClientHeartBeatEnable() && heartbeatExpireTime != null) {
            // 判断是否开启客户端心跳
            ctx.pipeline()
                    // 客户端登录保活处理器
                    .addAfter(MessageConstant.CONVERT_2_PACKET_HANDLER, MessageConstant.CLIENT_LOGIN_KEEP_ALIVE_HANDLER, new LoginKeepAliveHandler())
                    // 添加读写空闲处理器， 添加后，下条消息就可以接收心跳消息了
                    .addAfter(MessageConstant.CLIENT_LOGIN_KEEP_ALIVE_HANDLER, MessageConstant.HEART_BEAT_IDLE_HANDLER, new IdleStateHandler(heartbeatExpireTime, NumberConstant.NUMBER_0, NumberConstant.NUMBER_0))
                    // 处理心跳的以及相关逻辑都放在这里处理
                    .addAfter(MessageConstant.HEART_BEAT_IDLE_HANDLER, MessageConstant.HEART_BEAT_HANDLER, new HeartBeatHandler());
        }
        // 接收端回应登录设备登录成功信息
        // 同步发送登录成功消息给客户端
        message.setContentType(MessageContentTypeEnum.LOGIN_RESPONSE_SUCCESS_CONTENT.getType());
        message.setContent(MessageContentTypeEnum.LOGIN_RESPONSE_SUCCESS_CONTENT.getDescription());
        MessageHelper.syncSendMessage(notifyPacket, Target.newBuilder().targetIdentity(cacheLoginClientInfo.getIdentity()).targetServerAddress(cacheLoginClientInfo.getLoginServerAddress()).deviceType(deviceType).build());
        // 登录成功，取消定时任务
        boolean cancelled = cancelTimeoutFuture(ctx);
        if (!cancelled) {
            log.warn("客户端: {} 登录成功，取消登录超时定时任务失败", cacheLoginClientInfo);
        }
        // 发送客户端成功登录事件
        MessageServerContext.publishEvent(new ClientLoginEvent(cacheLoginClientInfo, ctx, loginTimestamp), true);
        // 取消该handle
        ctx.pipeline().remove(this);
    }

    /***
     * @author fzx
     * @description 校验登录信息
     */
    public boolean validate(LoginContent loginContent) {

        return true;
    }
}
