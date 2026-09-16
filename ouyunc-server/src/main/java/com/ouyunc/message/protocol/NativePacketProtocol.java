package com.ouyunc.message.protocol;


import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.LoginScopeEnum;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.Protocol;
import com.ouyunc.base.model.SendCallback;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.HttpUtil;
import com.ouyunc.message.cluster.client.pool.MessageClientPool;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.handler.*;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.helper.PacketChannelWriter;
import com.ouyunc.message.http.HttpRequestDispatcher;
import com.ouyunc.message.validator.AppKeyValidator;
import com.ouyunc.message.validator.LoginAuthValidator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
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
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.apache.commons.lang3.StringUtils;
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
                try {
                    URI uri = new URI(uriStr);
                    //封装参数传
                    Map<String, Object> queryParamsMap = HttpUtil.wrapParams2Map(uri.getQuery());
                    // 这里可以根据业务提前做appKey 的验证和appKey下连接数的统计，直接从queryParamsMap 这里面取值即可
                    // 如果这里提前做校验签名了，登录那边可校验可不校验；为什么在这里提前做签名验证？因为读写空闲的开启默认在登录成功后才开启，如果外部客户端或非法客户端通过ws协议只连接不做登录，那么就是无用的连接且会占用连接资源，所以这里提前做签名验证，如果签名验证失败，直接断开连接，这样连接资源会减少，同时不会占用连接资源；fast-fail
                    // appKey的连接数统计，为什么在这里做链接数的统计？因为为了防止签名验证通过后，外部或非法客户端不发送登录信息，则无法真实统计该客户端的真实连接数，无法对外部客户端提前做连接限制，可能造成非法连接过多，从而造成资源浪费，增加服务器压力；
                    if (!preVerifySignature(queryParamsMap) || !preVerifyAppKeyConnects(ctx, queryParamsMap)) {
                        log.error("客户端连接失败,原因：签名验证失败或验证统计AppKey连接数超过允许的最大值！");
                        ctx.close();
                        return;
                    }
                } catch (URISyntaxException e) {
                    log.error("客户端连接失败,原因：uri解析失败！正在关闭channel :{}", ctx.channel().id().asShortText());
                    ctx.channel().close();
                    return;
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
                                        WS_FILTER_PROVIDER, // 小帧跳过压缩
                                        NumberConstant.NUMBER_0
                                ),new DeflateFrameServerExtensionHandshaker(DeflateFrameServerExtensionHandshaker.DEFAULT_COMPRESSION_LEVEL,NumberConstant.NUMBER_0)))
                        //10485760
                        .addLast(MessageConstant.WS_SERVER_PROTOCOL_HANDLER, new WebSocketServerProtocolHandler(MessageServerContext.serverProperties().getWebsocketPath(), null, true, MessageConstant.MAX_WEBSOCKET_FRAME_SIZE))
                        // 转换成包packet,内部消息传递都是以packet 进行处理
                        .addLast(MessageConstant.CONVERT_2_PACKET_HANDLER, new Convert2PacketHandler())
                        // 统一业务入口：preProcess → process → postProcess
                        .addLast(MessageConstant.PACKET_HANDLER, PacketHandler.client())
                        // 在最后添加异常处理器
                        .addLast(MessageConstant.EXCEPTION_HANDLER, new ExceptionHandler())
                        // 移除协议分发器
                        .remove(MessageConstant.HTTP_DISPATCHER_HANDLER);
                // 登录认证（可选）；敏感词在 PacketHandler 有序任务内，不挂管道
                installAuth(ctx.pipeline());
                // 调用当前handler的下一个handle的active，注意与ctx.pipeline().fireChannelActive()
                ctx.fireChannelActive();
            }else {
                log.error("当前请求不是http 请求,正在关闭channel:{}", ctx.channel().id().asShortText());
            }

        }


        /**
         * 握手期连接配额：必须带 appKey 并预占本机额度；未登录关连时由 closeFuture 释放。
         */
        private boolean preVerifyAppKeyConnects(ChannelHandlerContext ctx, Map<String, Object> queryParamsMap) {
            String appKey = firstQuery(queryParamsMap, "appKey", "app_key");
            if (StringUtils.isBlank(appKey)) {
                log.error("WS 握手缺少 appKey，拒绝连接 channelId={}", ctx.channel().id().asShortText());
                return false;
            }
            return AppKeyValidator.INSTANCE.tryReserveForLogin(appKey, ctx);
        }

        /**
         * 握手期签名：四字段全缺则等 LOGIN 包；出现任一则必须齐全，否则拒绝（禁止半套签名绕过）。
         */
        private boolean preVerifySignature(Map<String, Object> queryParamsMap) {
            String appKey = firstQuery(queryParamsMap, "appKey", "app_key");
            String identity = firstQuery(queryParamsMap, "identity", "userId", "user_id");
            String signature = firstQuery(queryParamsMap, "signature", "sign");
            String createTimeRaw = firstQuery(queryParamsMap, "createTime", "create_time");
            int present = countPresent(identity, signature, createTimeRaw);
            if (present == 0) {
                return true;
            }
            if (present < 3 || StringUtils.isBlank(appKey)) {
                log.warn("WS 握手签名字段不全，拒绝连接");
                return false;
            }
            long createTime;
            try {
                createTime = Long.parseLong(createTimeRaw.trim());
            } catch (NumberFormatException e) {
                log.warn("WS 握手 createTime 非法: {}", createTimeRaw);
                return false;
            }
            LoginContent loginContent = new LoginContent();
            loginContent.setAppKey(appKey);
            loginContent.setIdentity(identity);
            loginContent.setSignature(signature);
            loginContent.setCreateTime(createTime);
            loginContent.setScope(LoginScopeEnum.NORMAL.getType());
            String algoRaw = firstQuery(queryParamsMap, "signatureAlgorithm", "signAlgo");
            if (StringUtils.isNotBlank(algoRaw)) {
                try {
                    loginContent.setSignatureAlgorithm(Byte.parseByte(algoRaw.trim()));
                } catch (NumberFormatException ignored) {
                    // 默认 MD5
                }
            }
            return LoginAuthValidator.verify(loginContent);
        }

        private static int countPresent(String... values) {
            int n = 0;
            if (values == null) {
                return 0;
            }
            for (String value : values) {
                if (StringUtils.isNotBlank(value)) {
                    n++;
                }
            }
            return n;
        }

        private static String firstQuery(Map<String, Object> queryParamsMap, String... keys) {
            if (queryParamsMap == null || keys == null) {
                return null;
            }
            for (String key : keys) {
                Object v = queryParamsMap.get(key);
                if (v != null && StringUtils.isNotBlank(String.valueOf(v))) {
                    return String.valueOf(v).trim();
                }
            }
            return null;
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
