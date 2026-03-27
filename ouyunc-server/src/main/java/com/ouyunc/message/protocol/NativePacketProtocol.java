package com.ouyunc.message.protocol;


import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;
import com.ouyunc.base.constant.enums.SendStatusEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.Protocol;
import com.ouyunc.base.model.SendCallback;
import com.ouyunc.base.model.SendResult;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.HttpUtil;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.message.cluster.client.pool.MessageClientPool;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.convert.PacketConverter;
import com.ouyunc.message.handler.*;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.http.HttpRequestDispatcher;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.pool.ChannelPool;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionFilterProvider;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.DeflateFrameServerExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * @Author fzx
 * @Description: 原生packet协议
 **/
public enum NativePacketProtocol implements PacketProtocol {


    // 处理ws/wss,这里相当于关键入口
    WS(ProtocolTypeEnum.WS.getProtocol(), ProtocolTypeEnum.WS.getProtocolVersion(), "websocket 协议，版本号为1") {
        private final EventExecutorGroup eventExecutorGroup = new DefaultEventExecutorGroup(Runtime.getRuntime().availableProcessors()* NumberConstant.NUMBER_2, new BasicThreadFactory.Builder().namingPattern("Ws-Protocol-Pool-%d").build());
        // WS 压缩过滤器：小于阈值不压缩，入站总是解压
        private static final WebSocketExtensionFilterProvider WS_FILTER_PROVIDER = new WebSocketExtensionFilterProvider() {
            private final WebSocketExtensionFilter thresholdFilter = frame -> frame.content() != null && frame.content().readableBytes() < MessageConstant.WEBSOCKET_COMPRESSION_THRESHOLD;

            @Override
            public WebSocketExtensionFilter encoderFilter() {
                return thresholdFilter; // 小于阈值跳过压缩
            }

            @Override
            public WebSocketExtensionFilter decoderFilter() {
                return WebSocketExtensionFilter.NEVER_SKIP; // 总是解压
            }
        };
        @Override
        public void doDispatcher(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof FullHttpRequest request) {
                // 获取真实ip 并设置
                String uriStr = request.uri();
                log.info("当前请求路径uri：{}", uriStr);
                try {
                    URI uri = new URI(uriStr);
                    //封装参数传
                    Map<String, Object> queryParamsMap = HttpUtil.wrapParams2Map(uri.getQuery());
                    // 这里可以根据业务提前做appKey 的验证和appKey下连接数的统计，直接从queryParamsMap 这里面取值即可
                    // 如果这里提前做校验签名了，登录那边可校验可不校验；为什么在这里提前做签名验证？因为读写空闲的开启默认在登录成功后才开启，如果外部客户端或非法客户端通过ws协议只连接不做登录，那么就是无用的连接且会占用连接资源，所以这里提前做签名验证，如果签名验证失败，直接断开连接，这样连接资源会减少，同时不会占用连接资源；fast-fail
                    // appKey的连接数统计，为什么在这里做链接数的统计？因为为了防止签名验证通过后，外部或非法客户端不发送登录信息，则无法真实统计该客户端的真实连接数，无法对外部客户端提前做连接限制，可能造成非法连接过多，从而造成资源浪费，增加服务器压力；
                    if (!preVerifySignature(queryParamsMap) || !preVerifyAppKeyConnects(queryParamsMap)) {
                        log.error("客户端连接失败,原因：签名验证失败或验证统计AppKey连接数超过允许的最大值！");
                        // 关闭连接
                        ctx.close();
                    }
                } catch (URISyntaxException e) {
                    log.error("客户端连接失败,原因：uri解析失败！正在关闭channel :{}", ctx.channel().id().asShortText());
                    ctx.channel().close();
                }
                ctx.channel().attr(protocolAttrKey).set(this);
                ctx.pipeline()
                        // 限制最大聚合帧，避免大包拖垮内存
                        .addLast(MessageConstant.WS_FRAME_AGGREGATOR_HANDLER, new WebSocketFrameAggregator(MessageConstant.MAX_WEBSOCKET_FRAME_SIZE))
                        // 开启压缩
                        .addLast(MessageConstant.WS_COMPRESSION_HANDLER, new WebSocketServerExtensionHandler(
                                new PerMessageDeflateServerExtensionHandshaker(
                                        NumberConstant.NUMBER_6,      // 压缩等级：1(快)~9(高压缩)，6折中
                                        true,   // allowServerWindowSize: 允许协商窗口大小
                                        NumberConstant.NUMBER_15,     // preferredServerWindowSize: 2^15
                                        true,   // allowServerNoContext: 允许无上下文（更少内存）
                                        false,  // preferredServerNoContext: 默认保留上下文（压缩率更好）
                                        WS_FILTER_PROVIDER // 小帧跳过压缩
                                ),new DeflateFrameServerExtensionHandshaker()))
                        //10485760
                        .addLast(MessageConstant.WS_SERVER_PROTOCOL_HANDLER, new WebSocketServerProtocolHandler(MessageServerContext.serverProperties().getWebsocketPath(), null, true, MessageConstant.MAX_WEBSOCKET_FRAME_SIZE))
                        // 转换成包packet,内部消息传递都是以packet 进行处理
                        .addLast(MessageConstant.CONVERT_2_PACKET_HANDLER, new Convert2PacketHandler())
                        // 添加监控处理逻辑
                        .addLast(MessageConstant.MONITOR_HANDLER, new MonitorHandler())
                        // 在业务处理之前可以进行登录认证处理，登录认证处理，如果不需要登录处理，可在配置文件中配置，不需要在这里处理
                        // 前置处理
                        .addLast(eventExecutorGroup, MessageConstant.PRE_HANDLER, new PacketPreHandler())
                        // 业务处理
                        .addLast(eventExecutorGroup, MessageConstant.WS_HANDLER, new PacketHandler())
                        // 后置处理
                        .addLast(eventExecutorGroup, MessageConstant.POST_HANDLER, new PacketPostHandler())
                        // 判断是否需要开启客户端心跳如果需要则开启客户端心跳，由于心跳消息不需要登录就可以，所以放在登录认证处理器前面
                        // 在最后添加异常处理器
                        .addLast(MessageConstant.EXCEPTION_HANDLER, new ExceptionHandler())
                        // 移除协议分发器
                        .remove(MessageConstant.HTTP_DISPATCHER_HANDLER);
                // 如果开启登录则添加登录认证处理器
                if (MessageServerContext.serverProperties().isServerLoginEnable()) {
                    ctx.pipeline().addBefore(MessageConstant.PRE_HANDLER, MessageConstant.AUTHENTICATION_HANDLER, new AuthenticationHandler());
                }
                // 调用当前handler的下一个handle的active，注意与ctx.pipeline().fireChannelActive()
                ctx.fireChannelActive();
            }else {
                log.error("当前请求不是http 请求,正在关闭channel:{}", ctx.channel().id().asShortText());
            }

        }


        /**
         *
         *  提前校验和统计appKey的连接数 预想连接数的存储使用缓存redis 的hash 结构， xxxx:connects:appKey     identity       登录信息（clientLoginInfo）,   由于还未登录登录信息暂时拿不到可以先空着或者使用1代替，当需要登录的时候再替代
         *  注意：在使用连接统计的时候，切记在断开连接的时候需要把 对应的连接减少
         * @param queryParamsMap
         * @return
         */
        private boolean preVerifyAppKeyConnects(Map<String, Object> queryParamsMap) {

            return true;
        }

        /**
         *  提前验证签名
         * @param queryParamsMap
         * @return
         */
        private boolean preVerifySignature(Map<String, Object> queryParamsMap) {
            // 根据业务自行处理，有些业务或appKey是不需要校验和签名的
            return true;
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


    // 目前该协议不对外开放只作为集群内部协议使用，可以对接jt 818,或者其他物联网的通信，字节扩充，
    OUYUNC(ProtocolTypeEnum.OUYUNC.getProtocol(), ProtocolTypeEnum.OUYUNC.getProtocolVersion(), "自定义ouyunc协议，版本号为1") {
        private final EventExecutorGroup eventExecutorGroup = new DefaultEventExecutorGroup(Runtime.getRuntime().availableProcessors()* NumberConstant.NUMBER_2, new BasicThreadFactory.Builder().namingPattern("Ouyunc-Protocol-Pool-%d").build());
        @Override
        public void doDispatcher(ChannelHandlerContext ctx,  Object msg) {
            ctx.channel().attr(protocolAttrKey).set(this);
            ctx.pipeline()
                    // 上一个packet编解码处理器，处理后，会在这里交给包转换器来转换
                    // 转换成包packet，这里为了做兼容客户端心跳
                    .addLast(MessageConstant.CONVERT_2_PACKET_HANDLER, new Convert2PacketHandler())
                    // 添加一个集群中处理消息路由的处理器，这样就不需要在业务处理器中都写一下了
                    .addLast(MessageConstant.PACKET_CLUSTER_ROUTER_HANDLER, new ClusterPacketRouteHandler())
                    // 集群内部/外部业务处理
                    .addLast(eventExecutorGroup, MessageConstant.OUYUNC_HANDLER, new PacketHandler())
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
                        EventLoop eventLoop = channel.eventLoop();
                        if (eventLoop.inEventLoop()) {
                            MessageHelper.tryWritePacketAndThen(channel, packet, sendCallback, releaseChannel);
                        } else if (!eventLoop.isTerminated() && !eventLoop.isShutdown() && !eventLoop.isShuttingDown()) {
                            eventLoop.execute(() -> MessageHelper.tryWritePacketAndThen(channel, packet, sendCallback, releaseChannel));
                        } else {
                            releaseChannel.run();
                            log.error("发送消息时，channel.eventLoop 被终止或关闭； channelId: {}", channel.id().asShortText());
                            sendCallback.onCallback(SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(new MessageException("发送消息时，channel.eventLoop 被终止或关闭!")).build());
                        }
                    } else {
                        // 获取失败
                        Throwable e = acquireFuture.cause();
                        log.error("获取集群中远端channel失败：{}", e.getMessage());
                        sendCallback.onCallback(SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(e).build());
                    }
                }
            });
        }

    },


    //mqtt
    MQTT(ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), "mqtt协议，版本号为v3.1/v3.1.1/v5.0") {

        private final EventExecutorGroup eventExecutorGroup = new DefaultEventExecutorGroup(Runtime.getRuntime().availableProcessors()* NumberConstant.NUMBER_2,new BasicThreadFactory.Builder().namingPattern("Mqtt-Protocol-Pool-%d").build());

        @Override
        public void doDispatcher(ChannelHandlerContext ctx,  Object msg) {
            ctx.channel().attr(protocolAttrKey).set(this);
            ChannelPipeline pipeline = ctx.pipeline();
            pipeline.addLast(MessageConstant.MQTT_DECODER_HANDLER, new MqttDecoder())
                    .addLast(MessageConstant.MQTT_ENCODER_HANDLER, MqttEncoder.INSTANCE)
                    .addLast(MessageConstant.CONVERT_2_PACKET_HANDLER, new Convert2PacketHandler())
                    // 添加监控处理逻辑
                    .addLast(MessageConstant.MONITOR_HANDLER, new MonitorHandler())
                    // 前置处理
                    .addLast(eventExecutorGroup, MessageConstant.PRE_HANDLER, new PacketPreHandler())
                    // 业务处理
                    .addLast(eventExecutorGroup, MessageConstant.MQTT_SERVER_HANDLER, new PacketHandler())
                    // 后置处理
                    .addLast(eventExecutorGroup, MessageConstant.POST_HANDLER, new PacketPostHandler())
                    // 异常处理器
                    .addLast(MessageConstant.EXCEPTION_HANDLER, new ExceptionHandler());
            // 如果开启登录则添加登录认证处理器
            if (MessageServerContext.serverProperties().isServerLoginEnable()) {
                ctx.pipeline().addBefore(MessageConstant.PRE_HANDLER, MessageConstant.AUTHENTICATION_HANDLER, new AuthenticationHandler());
            }
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
                SendResult sendResult = SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(new MessageException("发送消息时，ctx 不存在； 请检查客户端是否登录")).build();
                sendCallback.onCallback(sendResult);
                MessageServerContext.publishEvent(new MessageEvent(sendResult, MessageEventTypeEnum.SEND_FAIL), true);
                return;
            }
            Channel channel = ctx.channel();
            if (channel.isActive() && channel.isWritable()) {
                // 如果channel是活跃的,可写的，高水位低水位，则直接写出去
                for (PacketConverter<?> packetConverter : MessageServerContext.packetConverterList) {
                    // 注意：这里转换后，不要将metadata 置空，但是发送出去的消息，建议不要带元数据
                    Object msg = packetConverter.convertFromPacket(packet);
                    if (msg != null) {
                        // 将消息写到channel,避免线程安全问题
                        EventLoop eventLoop = channel.eventLoop();
                        if (eventLoop.inEventLoop()) {
                            MessageHelper.tryWriteObject(channel, msg, packet, sendCallback);
                        } else if (!eventLoop.isTerminated() && !eventLoop.isShutdown() && !eventLoop.isShuttingDown()) {
                            // 如果不是 EventLoop 线程，将任务提交到 EventLoop 线程中执行；
                            eventLoop.execute(() -> {
                                MessageHelper.tryWriteObject(channel, msg, packet, sendCallback);
                            });
                        }else {
                            log.error("发送消息时，channel.eventLoop 被终止或关闭； channelId: {}", channel.id().asShortText());
                            SendResult sendResult = SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(new MessageException("发送消息时，channel.eventLoop 被终止或关闭！")).build();
                            sendCallback.onCallback(sendResult);
                            MessageServerContext.publishEvent(new MessageEvent(sendResult, MessageEventTypeEnum.SEND_FAIL), true);
                        }
                        return;
                    }
                }
                log.error("发送消息时，packet: {} 转换其他协议发生异常,找不到匹配的协议转换器！", packet);
                SendResult sendResult = SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(new MessageException("发送消息时，packet转换其他协议发生异常,找不到匹配的协议转换器！")).build();
                sendCallback.onCallback(sendResult);
                MessageServerContext.publishEvent(new MessageEvent(sendResult, MessageEventTypeEnum.SEND_FAIL), true);
            } else {
                log.error("通道channel：{} 不可用或不可写, 使得消息packet: {} 发送给用户: {} 失败!", channel.id().asShortText(), packet, to);
                SendResult sendResult = SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(new MessageException("发送消息时，通道channel：" + channel.id().asShortText() + " 不可用或不可写！")).build();
                sendCallback.onCallback(sendResult);
                MessageServerContext.publishEvent(new MessageEvent(sendResult, MessageEventTypeEnum.SEND_FAIL), true);
            }
        } catch (Exception e) {
            log.error("消息packet: {} 发送给用户: {} 失败!", packet, to);
            // 消息丢失
            SendResult sendResult = SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(e).build();
            sendCallback.onCallback(sendResult);
            MessageServerContext.publishEvent(new MessageEvent(sendResult, MessageEventTypeEnum.SEND_FAIL), true);
        }
    }
    
}
