package com.ouyunc.message.channel;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.SSLUtil;
import com.ouyunc.core.listener.event.ClientLogoutEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.dispatcher.ProtocolDispatcher;
import com.ouyunc.message.handler.MessageLoggingHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.util.concurrent.TimeUnit;

/**
 * @Author fzx
 * @Description: 默认初始化 socket channel
 **/
public class DefaultSocketChannelInitializer extends SocketChannelInitializer {
    private static final Logger log = LoggerFactory.getLogger(DefaultSocketChannelInitializer.class);


    /**
     * @Author fzx
     * @Description 初始化 socket channel
     */
    @Override
    void initSocketChannel(SocketChannel socketChannel) {
        // 这里只设置协议分发器，具体可参照netty 的源码例子
        ChannelPipeline pipeline = socketChannel.pipeline();
        // 是否开启SSL/TLS
        if (MessageServerContext.serverProperties().isSslEnable()) {
            // 这个处理器需要放到第一位
            SSLUtil.configSSL(channel -> {
                SSLEngine sslEngine = SSLUtil.buildServerSslContext(MessageServerContext.serverProperties().getSslCertificate(), MessageServerContext.serverProperties().getSslPrivateKey()).newEngine(channel.alloc());
                // 服务器端模式，客户端模式设置为true
                sslEngine.setUseClientMode(false);
                // 不需要验证客户端，客户端不设置该项；  SSL/TLS 开启后有多种认证方式：1-不需要认证，2-单向认证（一般是客户端认证），3-双向认证
                sslEngine.setNeedClientAuth(false);
                channel.pipeline().addFirst(MessageConstant.SSL_HANDLER, new SslHandler(sslEngine));
            }, socketChannel);
        }
        // 日志处理器
        pipeline.addLast(MessageConstant.LOG_HANDLER, new MessageLoggingHandler(MessageServerContext.serverProperties().getLogLevel()));
        // 协议分发器
        pipeline.addLast(MessageConstant.PROTOCOL_DISPATCHER_HANDLER, new ProtocolDispatcher());
        // 每一个客户端连接都会走这里的逻辑，且被监听关闭事件，并作出相关处理逻辑
        // 有以下几种关闭的场景：
        // 一,单服务情况：
        // 1, 服务宕机，导致本地注册表消失，客户端在分布式缓存的登录信息在一定时间（可能是永久）未过期；有两种方式使其分布式缓存失效，（1），自动等待分布式过期，无需处理，（2），使用相同的设备进行再次登录
        // 添加监听器,如果有外部客户端(可以理解为业务channel)channel关闭,则处理登出逻辑
        socketChannel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isDone()) {
                    if (future.isSuccess()) {
                        log.warn("================客户端关闭了,正在处理客户端 channel id:  {}==================", socketChannel.id().asShortText());
                        // 解绑已绑定的外部用户
                        //1,从channel中的attrMap取出相关属性
                        final LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(socketChannel, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
                        if (loginClientInfo != null) {
                            // 这里不进行判空了，到这里肯定不为空（登录信息里面一定要有登录设备的类型）
                            String clientLoginDeviceName = loginClientInfo.getDeviceType().getDeviceTypeName();
                            String comboIdentity = IdentityUtil.generalComboIdentity(loginClientInfo.getAppKey(), loginClientInfo.getIdentity(), clientLoginDeviceName);
                            // 登录信息一致,才进行解绑，删除缓存信息
                            MessageServerContext.localClientRegisterTable.delete(comboIdentity);
                            String loginClientInfoCacheKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + loginClientInfo.getAppKey() + CacheConstant.COLON + CacheConstant.LOGIN + CacheConstant.USER + comboIdentity;
                            // 获取分布式锁, 这里使用锁的目的，可以参考登录处理器的分布式锁，防止重复解绑 LoginMessageProcessor
                            RLock lock = MessageServerContext.redissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + loginClientInfo.getAppKey() + CacheConstant.COLON + comboIdentity);
                            try {
                                if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                                    LoginClientInfo cacheLoginClientInfo = MessageServerContext.remoteLoginClientInfoCache.get(loginClientInfoCacheKey);
                                    // 这里比较两个登录服务器地址是否一致的目的是因为，无论集群还是单服务 在ctx异步关闭时,有可能存在关闭的执行顺序比绑定客户端的方法执行的慢，导致缓存被覆盖，结果又给删除了缓存信息，导致数据错乱。
                                    if (cacheLoginClientInfo != null && loginClientInfo.getLoginServerAddress().equals(cacheLoginClientInfo.getLoginServerAddress()) && cacheLoginClientInfo.getLastLoginTime() == loginClientInfo.getLastLoginTime()) {
                                        // 缓存中有没有登录信息都进行删除下
                                        MessageServerContext.remoteLoginClientInfoCache.delete(loginClientInfoCacheKey);
                                    }else {
                                        log.warn("客户端: {} 解绑登录信息失败,原因：缓存中不存在登录信息或登录地址不匹配", loginClientInfo);
                                    }
                                }else {
                                    log.error("客户端: {} 绑定登录信息失败,原因：获取分布式锁失败", loginClientInfo);
                                }
                            } catch (Exception e) {
                                log.error("客户端: {} 绑定登录信息失败,原因：{}", loginClientInfo, e.getMessage());
                                throw new MessageException(e);
                            } finally {
                                if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                                    lock.unlock();
                                }
                            }
                            // 发送客户端离线事件， 可以处理发送遗嘱等客户端关闭后的操作逻辑
                            MessageServerContext.publishEvent(new ClientLogoutEvent(loginClientInfo), true);
                        }
                    }
                }
            }
        });
    }


}
