package com.ouyunc.client.pool;


import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;
import com.ouyunc.base.model.Protocol;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.client.selector.ProtocolSelector;
import com.ouyunc.client.selector.WebsocketProtocolDispatcherProcessor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 也可以通过继承AbstractChannelPoolHandler 来实现动态的channel 管理与释放，集群内置客户端的管道处理
 **/
public class MessageClientChannelPoolHandler extends AbstractChannelPoolHandler {
    private static final Logger log = LoggerFactory.getLogger(MessageClientChannelPoolHandler.class);


    /**
     * @Author fzx
     * @Description channel 的创建,初次与服务端建立连接的时候会创建channel，内置客户端只做发送处理，不牵涉到粘包，半包，拿到的就是一个完整的包
     * 这个链接池，所有协议以及包类型的信息都会走这里
     */
    @Override
    public void channelCreated(Channel channel) throws Exception {
        log.info("内部客户端channel池，创建通道channel: {}", channel.id().asShortText());
        // 获取这个管道应该所属的协议
        Protocol protocol = channel.attr(AttributeKey.<Protocol>valueOf(MessageConstant.BOOTSTRAP_ATTR_KEY_TAG_CLIENT)).get();
        // 获取到协议，添加协议处理器
        ChannelPipeline pipeline = channel.pipeline();
//        if (MessageContext.messageProperties.isSslEnable()) {
//            // 这个处理器需要放到第一位
//            SSLUtil.configSSL(ch -> {
//                SSLEngine sslEngine = SSLUtil.buildClientSslContext(MessageContext.messageProperties.getSslCertificate(), MessageContext.messageProperties.getSslPrivateKey()).newEngine(channel.alloc());
//                // 客户端模式
//                sslEngine.setUseClientMode(true);
//                // 不进行客户端校验
//                sslEngine.setNeedClientAuth(false);
//                pipeline.addFirst(new SslHandler(sslEngine));
//            }, channel);
//        }
        // 添加消息处理器链
        pipeline.addLast(MessageConstant.LOG_HANDLER, new LoggingHandler(LogLevel.DEBUG));
        ProtocolSelector<Protocol, Channel> protocolSelector = getProtocolSelector(protocol);
        if (protocolSelector != null) {
            protocolSelector.process(channel);
        }
        // 添加监听器,如果有内部客户端channel 关闭，则移除channel，（包括动态关闭核心channel）
        channel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isDone()) {
                    if (future.isSuccess()) {
                        // 客户端关闭， 做些处理
                        log.error("协议类型： {}  的  channel id {} 关闭了", channel.id().asShortText(), protocol);
                        // 从管道中取出登录的信息
                        //1,从channel中的attrMap取出相关属性
                        ProtocolTypeEnum protocolType = ChannelAttrUtil.getChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
                        // 如果登录客户端不为空，则发送客户端离线事件
                        if (protocolType != null) {
                            ChannelAttrUtil.setChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN, null);
                            // @todo 这里可以发布离线事件
                        }
                    }
                }
            }
        });
    }

    /***
     * @author fzx
     * @description 获取协议选择器
     */
    public ProtocolSelector<Protocol, Channel> getProtocolSelector(Protocol protocol) {
        // 匹配并获取协议分发器
//        for (ProtocolSelector<Protocol, Channel> protocolSelector : MessageContext.protocolDispatcherProcessors) {
//            if (protocolSelector.match(protocol)) {
//                return protocolSelector;
//            }
//        }
        return new WebsocketProtocolDispatcherProcessor();
    }

}
