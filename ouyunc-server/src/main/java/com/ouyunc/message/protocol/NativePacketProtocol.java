package com.ouyunc.message.protocol;


import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.Protocol;
import com.ouyunc.base.model.SendCallback;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.message.cluster.client.pool.MessageClientPool;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.handler.*;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.helper.PacketChannelWriter;
import com.ouyunc.message.http.HttpRequestDispatcher;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.pool.ChannelPool;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 原生packet协议
 **/
public enum NativePacketProtocol implements PacketProtocol {


    // 处理ws/wss,这里相当于关键入口
    WS(ProtocolTypeEnum.WS.getProtocol(), ProtocolTypeEnum.WS.getProtocolVersion(), "websocket 协议，版本号为1") {
        @Override
        public void doDispatcher(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof FullHttpRequest request) {
                WsHandshakeSupport.dispatch(ctx, request);
                return;
            }
            log.error("当前请求不是http 请求,正在关闭channel:{}", ctx.channel().id().asShortText());
        }
    },


    //处理 http/https
    HTTP(ProtocolTypeEnum.HTTP.getProtocol(), ProtocolTypeEnum.HTTP.getProtocolVersion(), "http协议，版本号为1") {
        @Override
        public void doDispatcher(ChannelHandlerContext ctx,  Object msg) {
            ctx.channel().attr(protocolAttrKey).set(this);
            if (msg instanceof FullHttpRequest request) {
                HttpRequestDispatcher.getInstance().dispatch(ctx, request);
            }
        }
    },


    // 集群内部原生 Packet：HMAC + 租约 + 集群路由；不对外部客户端开放。
    OUYUNC(ProtocolTypeEnum.OUYUNC.getProtocol(), ProtocolTypeEnum.OUYUNC.getProtocolVersion(), "集群内部 ouyunc 协议，版本号为1") {
        @Override
        public void doDispatcher(ChannelHandlerContext ctx,  Object msg) {
            ctx.channel().attr(protocolAttrKey).set(this);
            ctx.pipeline()
                    // 上一个packet编解码处理器，处理后，会在这里交给包转换器来转换
                    // 转换成包packet，这里为了做兼容客户端心跳
                    .addLast(MessageConstant.CONVERT_2_PACKET_HANDLER, new Convert2PacketHandler())
                    // 添加一个集群中处理消息路由的处理器，这样就不需要在业务处理器中都写一下了
                    .addLast(MessageConstant.PACKET_CLUSTER_ROUTER_HANDLER, new ClusterPacketRouteHandler())
                    // 集群内部业务处理：process → postProcess
                    .addLast(MessageConstant.OUYUNC_HANDLER, PacketHandler.cluster())
                    // 在最后添加异常处理器
                    .addLast(MessageConstant.EXCEPTION_HANDLER, new ExceptionHandler())
                    // 移除协议分发器
                    .remove(MessageConstant.PACKET_DISPATCHER_HANDLER);
            // 调用下一个handle的active
            ctx.fireChannelActive();
        }

        /***
         * @author fzx
         * @description 重写发送消息逻辑，主要是针对集群内部消息发送
         */
        @Override
        public void doSendMessage(Packet packet, String to, SendCallback sendCallback) {
            // 合并获取连接池
            // 先从活跃的channelPool缓存中获取，如果没有再从全局的channelPool缓存中获取
            ChannelPool channelPool = MessageServerContext.clusterActiveServerRegistryTableCache.get(to);
            if (channelPool == null) {
                channelPool = MessageServerContext.clusterGlobalServerRegistryTableCache.get(to);
            }
            // 判断是否有连接池，如果没有则创建新的连接池
            if (channelPool == null) {
                log.warn("有新的服务 {} 加入集群，正在尝试与其确认ack", to);
                try {
                    channelPool = MessageClientPool.clientSimpleChannelPoolMap.get(to);
                }catch (Exception e) {
                    log.error("通过参数to: {} , 获取/创建channelPool异常， 原因：{}", to, e.getMessage());
                    throw new MessageException(e);
                }
            }
            final ChannelPool finalChannelPool = channelPool;
            // 从连接池中获取一个连接
            Future<Channel> channelFuture = channelPool.acquire();
            channelFuture.addListener((FutureListener<Channel>) acquireFuture -> {
                if (acquireFuture.isDone()) {
                    // 判断是否连接成功
                    if (acquireFuture.isSuccess()) {
                        // 获取连接
                        Channel channel = acquireFuture.getNow();
                        // 给该通道打上标签(如果该通道channel 上有标签则不需要再打标签),打上标签的目的，是为了以后动态回收该channel,保证核心channel数
                        Integer channelPoolHashCode = ChannelAttrUtil.getChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_TAG_POOL);
                        if (channelPoolHashCode == null) {
                            ChannelAttrUtil.setChannelAttribute(channel, MessageConstant.CHANNEL_ATTR_KEY_TAG_POOL, finalChannelPool.hashCode());
                        }
                        // 客户端将数据写出到中介管道中；在写完成后再归还 channel
                        Runnable releaseChannel = () -> finalChannelPool.release(channel);
                        PacketChannelWriter.runOnEventLoop(channel, packet, sendCallback,
                                () -> PacketChannelWriter.tryWritePacketAndThen(channel, packet, sendCallback, releaseChannel),
                                releaseChannel);
                    } else {
                        // 获取失败
                        Throwable e = acquireFuture.cause();
                        log.error("获取集群中远端channel失败：{}", e.getMessage());
                        MessageHelper.notifySendFail(packet, e, sendCallback);
                    }
                }
            });
        }

    },

    /**
     * 外部客户端原生 Packet：编解码复用 Packet，业务路径与 WS/MQTT 一致（client 三阶段 + 登录鉴权），
     * 禁止集群路由 / 集群消息类型。
     */
    OUYUNC_CLIENT(ProtocolTypeEnum.OUYUNC_CLIENT.getProtocol(), ProtocolTypeEnum.OUYUNC_CLIENT.getProtocolVersion(),
            "客户端原生 ouyunc 协议，版本号为1") {
        @Override
        public void doDispatcher(ChannelHandlerContext ctx, Object msg) {
            ctx.channel().attr(protocolAttrKey).set(this);
            ctx.pipeline()
                    .addLast(MessageConstant.CONVERT_2_PACKET_HANDLER, new Convert2PacketHandler())
                    .addLast(MessageConstant.PACKET_HANDLER, PacketHandler.client())
                    .addLast(MessageConstant.EXCEPTION_HANDLER, new ExceptionHandler())
                    .remove(MessageConstant.PACKET_DISPATCHER_HANDLER);
            installAuth(ctx.pipeline());
            ctx.fireChannelActive();
        }
    },


    //mqtt
    MQTT(ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), "mqtt协议，版本号为v3.1/v3.1.1/v5.0") {

        @Override
        public void doDispatcher(ChannelHandlerContext ctx,  Object msg) {
            ctx.channel().attr(protocolAttrKey).set(this);
            ChannelPipeline pipeline = ctx.pipeline();
            pipeline.addLast(MessageConstant.MQTT_DECODER_HANDLER, new MqttDecoder())
                    .addLast(MessageConstant.MQTT_ENCODER_HANDLER, MqttEncoder.INSTANCE)
                    .addLast(MessageConstant.CONVERT_2_PACKET_HANDLER, new Convert2PacketHandler())
                    // 统一业务入口：preProcess → process → postProcess
                    .addLast(MessageConstant.PACKET_HANDLER, PacketHandler.client())
                    // 异常处理器
                    .addLast(MessageConstant.EXCEPTION_HANDLER, new ExceptionHandler());
            // MQTT 以 CONNECT 为登录，不挂 AuthenticationHandler（否则非 LOGIN 类型会被「请先登录」关掉）
            pipeline.addBefore(MessageConstant.PACKET_HANDLER, MessageConstant.LOGIN_TIMEOUT_HANDLER,
                    LoginTimeoutHandler.INSTANCE);
            // 移除掉掉协议分发器
            MqttProtocolDispatcherHandler mqttProtocolDispatcherHandler = pipeline.get(MqttProtocolDispatcherHandler.class);
            if (mqttProtocolDispatcherHandler != null) {
                pipeline.remove(MqttProtocolDispatcherHandler.class);
            }
            HttpProtocolDispatcherHandler httpProtocolDispatcherHandler = pipeline.get(HttpProtocolDispatcherHandler.class);
            if (httpProtocolDispatcherHandler != null) {
                pipeline.remove(HttpProtocolDispatcherHandler.class);
            }
            // 调用下一个handle的active
            ctx.fireChannelActive();
        }


    }


    ;

    /**
     * 需要登录时挂 AuthenticationHandler；内容安全在 {@link PacketHandler} 有序任务内执行。
     *
     * @param pipeline 当前连接管道
     */
    private static void installAuth(ChannelPipeline pipeline) {
        if (MessageServerContext.serverProperties().isServerLoginEnable()) {
            pipeline.addBefore(MessageConstant.PACKET_HANDLER, MessageConstant.AUTHENTICATION_HANDLER, new AuthenticationHandler());
        }
    }

    private static final Logger log = LoggerFactory.getLogger(NativePacketProtocol.class);
    public static final AttributeKey<Protocol> protocolAttrKey = AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_TAG_PROTOCOL_TYPE);

    /**
     * 协议编号
     */
    private byte protocol;

    /**
     * 协议版本
     */
    private byte protocolVersion;

    /**
     * 协议描述
     */
    private String description;

    NativePacketProtocol(byte protocol, byte protocolVersion, String description) {
        this.protocol = protocol;
        this.protocolVersion = protocolVersion;
        this.description = description;
    }


    public byte getProtocol() {
        return protocol;
    }

    public void setProtocol(byte protocol) {
        this.protocol = protocol;
    }

    public byte getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(byte protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /***
     * @author fzx
     * @description 获取协议
     */
    public static PacketProtocol prototype(byte protocol, byte protocolVersion) {
        for (NativePacketProtocol messageProtocol : NativePacketProtocol.values()) {
            if (messageProtocol.protocol == protocol && messageProtocol.protocolVersion == protocolVersion) {
                return messageProtocol;
            }
        }
        return null;
    }

    /**
     * @param ctx
     * @param msg 请求参数
     * @return void
     * @Author fzx
     * @Description 协议分发器
     */
    @Override
    public void doDispatcher(ChannelHandlerContext ctx,  Object msg) {
        throw new MessageException("请完善对应协议分发器, channelId=" + ctx.channel().id().asShortText());
    }
    /**
     * @param packet       消息包
     * @param to           接受者,组合唯一值
     * @param sendCallback 这个发送的回调，针对成功来说，只是理论上的成功，因为writeAndFlush 本身就是异步的，加上网络的不稳定性，很难严格意义上的判断发送成功
     * @return void
     * @Author fzx
     * @Description
     */
    @Override
    public void doSendMessage(Packet packet, String to, SendCallback sendCallback) {
        try {
            //从用户注册表中，获取用户对应的channel然后将消息写出去
            ChannelHandlerContext ctx = MessageServerContext.localLoginClientRegisterTable.get(to);
            if (ctx == null) {
                // 注意：如果走到了这里，可能是客户端注销了，qos 在重试，找不到ctx
                log.error("发送消息时，ctx 不存在； 请检查客户端 {} 是否登录", to);
                MessageHelper.notifySendFail(packet, "发送消息时，ctx 不存在； 请检查客户端是否登录", sendCallback);
                return;
            }
            Channel channel = ctx.channel();
            PacketChannelWriter.writeConverted(channel, packet, sendCallback);
        } catch (Exception e) {
            log.error("消息packet: {} 发送给用户: {} 失败!", packet, to);
            // 消息丢失
            MessageHelper.notifySendFail(packet, e, sendCallback);
        }
    }
    
}
