package com.ouyunc.message.protocol;


import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;
import com.ouyunc.base.constant.enums.SendStatusEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.exception.handler.MessageExceptionHandler;
import com.ouyunc.base.model.Protocol;
import com.ouyunc.base.model.SendCallback;
import com.ouyunc.base.model.SendResult;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.event.SendFailEvent;
import com.ouyunc.message.cluster.client.pool.MessageClientPool;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.convert.PacketConverter;
import com.ouyunc.message.handler.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.pool.ChannelPool;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * @Author fzx
 * @Description: 原生packet协议
 **/
public enum NativePacketProtocol implements PacketProtocol {

    // 处理ws/wss,这里相当于关键入口
    WS(ProtocolTypeEnum.WS.getProtocol(), ProtocolTypeEnum.WS.getProtocolVersion(), "websocket 协议，版本号为1") {
        @Override
        public void doDispatcher(ChannelHandlerContext ctx, Map<String, Object> queryParamsMap) {
            ctx.channel().attr(protocolAttrKey).set(this);
            ctx.pipeline()
                    //10 * 1024 * 1024
                    .addLast(MessageConstant.WS_FRAME_AGGREGATOR_HANDLER, new WebSocketFrameAggregator(Integer.MAX_VALUE))
                    // 开启压缩
                    .addLast(MessageConstant.WS_COMPRESSION_HANDLER, new WebSocketServerCompressionHandler())
                    //10485760
                    .addLast(MessageConstant.WS_SERVER_PROTOCOL_HANDLER, new WebSocketServerProtocolHandler(MessageServerContext.serverProperties().getWebsocketPath(), null, true, Integer.MAX_VALUE))
                    // 转换成包packet,内部消息传递都是以packet 进行处理
                    .addLast(MessageConstant.CONVERT_2_PACKET_HANDLER, new Convert2PacketHandler())
                    // 添加监控处理逻辑
                    .addLast(MessageConstant.MONITOR_HANDLER, new MonitorHandler())
                    // 在业务处理之前可以进行登录认证处理，登录认证处理，如果不需要登录处理，可在配置文件中配置，不需要在这里处理
                    // 前置处理
                    .addLast(MessageConstant.PRE_HANDLER, new PacketPreHandler())
                    // 业务处理
                    .addLast(MessageConstant.WS_HANDLER, new PacketHandler())
                    // 后置处理
                    .addLast(MessageConstant.POST_HANDLER, new PacketPostHandler());
            // 判断是否需要开启客户端心跳如果需要则开启客户端心跳，由于心跳消息不需要登录就可以，所以放在登录认证处理器前面
            // 在最后添加异常处理器
            ctx.pipeline().addLast(MessageConstant.EXCEPTION_HANDLER, new MessageExceptionHandler());
            // 移除协议分发器
            ctx.pipeline().remove(MessageConstant.HTTP_DISPATCHER_HANDLER);
            // 调用当前handler的下一个handle的active，注意与ctx.pipeline().fireChannelActive()
            ctx.fireChannelActive();
        }


    },


    //处理 http/https
    HTTP(ProtocolTypeEnum.HTTP.getProtocol(), ProtocolTypeEnum.HTTP.getProtocolVersion(), "http协议，版本号为1") {
        @Override
        public void doDispatcher(ChannelHandlerContext ctx, Map<String, Object> queryParamsMap) {
            ctx.channel().attr(protocolAttrKey).set(this);

        }

    },


    // 目前该协议不对外开放只作为集群内部协议使用，可以对接jt 818,或者其他物联网的通信，字节扩充，
    OUYUNC(ProtocolTypeEnum.OUYUNC.getProtocol(), ProtocolTypeEnum.OUYUNC.getProtocolVersion(), "自定义ouyunc协议，版本号为1") {
        @Override
        public void doDispatcher(ChannelHandlerContext ctx, Map<String, Object> queryParamsMap) {
            ctx.channel().attr(protocolAttrKey).set(this);
            ctx.pipeline()
                    // 上一个packet编解码处理器，处理后，会在这里交给包转换器来转换
                    // 转换成包packet，这里为了做兼容客户端心跳
                    .addLast(MessageConstant.CONVERT_2_PACKET_HANDLER, new Convert2PacketHandler())
                    // 添加一个集群中处理消息路由的处理器，这样就不需要在业务处理器中都写一下了
                    .addLast(MessageConstant.PACKET_CLUSTER_ROUTER_HANDLER, new ClusterPacketRouteHandler())
                    // 集群内部/外部业务处理
                    .addLast(MessageConstant.OUYUNC_HANDLER, new PacketHandler())
                    // 在最后添加异常处理器
                    .addLast(MessageConstant.EXCEPTION_HANDLER, new MessageExceptionHandler())
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
            Future<Channel> channelFuture = finalChannelPool.acquire();
            channelFuture.addListener((FutureListener<Channel>) acquireFuture -> {
                if (acquireFuture.isDone()) {
                    // 判断是否连接成功
                    if (acquireFuture.isSuccess()) {
                        // 获取连接
                        Channel channel = acquireFuture.getNow();
                        // 给该通道打上标签(如果该通道channel 上有标签则不需要再打标签),打上标签的目的，是为了以后动态回收该channel,保证核心channel数
                        AttributeKey<Integer> channelTagPoolKey = AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_TAG_POOL);
                        Integer channelPoolHashCode = channel.attr(channelTagPoolKey).get();
                        if (channelPoolHashCode == null) {
                            channel.attr(channelTagPoolKey).set(finalChannelPool.hashCode());
                        }
                        // 客户端将数据写出到中介管道中
                        channel.writeAndFlush(packet).addListener((ChannelFutureListener) future -> {
                            if (channelFuture.isDone()) {
                                if (channelFuture.isSuccess()) {
                                    sendCallback.onCallback(SendResult.builder().sendStatus(SendStatusEnum.SEND_OK).packet(packet).build());
                                }else {
                                    sendCallback.onCallback(SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(future.cause()).build());
                                }
                            }
                        });
                        // 用完后进行释放掉
                        finalChannelPool.release(channel);
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
        @Override
        public void doDispatcher(ChannelHandlerContext ctx, Map<String, Object> queryParamsMap) {
            ctx.channel().attr(protocolAttrKey).set(this);
            ChannelPipeline pipeline = ctx.pipeline();
            pipeline.addLast(MessageConstant.MQTT_DECODER_HANDLER, new MqttDecoder())
                    .addLast(MessageConstant.MQTT_ENCODER_HANDLER, MqttEncoder.INSTANCE)
                    .addLast(MessageConstant.CONVERT_2_PACKET_HANDLER, new Convert2PacketHandler())
                    // 添加监控处理逻辑
                    .addLast(MessageConstant.MONITOR_HANDLER, new MonitorHandler())
                    // 前置处理
                    .addLast(MessageConstant.PRE_HANDLER, new PacketPreHandler())
                    // 业务处理
                    .addLast(MessageConstant.MQTT_SERVER_HANDLER, new PacketHandler())
                    // 后置处理
                    .addLast(MessageConstant.POST_HANDLER, new PacketPostHandler())
                    .addLast(MessageConstant.EXCEPTION_HANDLER, new MessageExceptionHandler());
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
     * @Author fzx
     * @Description 协议分发器
     * @param ctx
     * @param queryParamsMap 请求参数
     * @return void
     */
    @Override
    public void doDispatcher(ChannelHandlerContext ctx, Map<String, Object> queryParamsMap) {

    }

    /**
     * @Author fzx
     * @Description
     * @param packet 消息包
     * @param to 接受者,组合唯一值
     * @param sendCallback 这个发送的回调，针对成功来说，只是理论上的成功，因为writeAndFlush 本身就是异步的，加上网络的不稳定性，很难严格意义上的判断发送成功
     * @return void
     */
    @Override
    public void doSendMessage(Packet packet, String to, SendCallback sendCallback) {
        try {
            //从用户注册表中，获取用户对应的channel然后将消息写出去
            ChannelHandlerContext ctx = MessageServerContext.localClientRegisterTable.get(to);
            Channel channel = ctx.channel();
            if (channel.isActive() && channel.isWritable()) {
                // 如果channel是活跃的,可写的，高水位低水位，则直接写出去
                for (PacketConverter<?> packetConverter : MessageServerContext.packetConverterList) {
                    // 注意：这里转换后，不要将metadata 置空，但是发送出去的消息，建议不要带元数据
                    Object msg = packetConverter.convertFromPacket(packet);
                    if (msg != null) {
                        // 将消息写到channel
                        channel.writeAndFlush(msg).addListener(future -> {
                            if (future.isDone()) {
                                if (future.isSuccess()) {
                                    // 回调成功
                                    sendCallback.onCallback(SendResult.builder().sendStatus(SendStatusEnum.SEND_OK).packet(packet).build());
                                }else {
                                    // 回调失败
                                    SendResult sendResult = SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(future.cause()).build();
                                    sendCallback.onCallback(sendResult);
                                    MessageServerContext.publishEvent(new SendFailEvent(sendResult), true);
                                }
                            }
                        });
                        return;
                    }
                }
                log.error("发送消息时，packet: {} 转换其他协议发生异常,找不到匹配的协议转换器！", packet);
                SendResult sendResult = SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(new MessageException("发送消息时，packet转换其他协议发生异常,找不到匹配的协议转换器！")).build();
                sendCallback.onCallback(sendResult);
                MessageServerContext.publishEvent(new SendFailEvent(sendResult), true);
            } else {
                log.error("通道channel：{} 不可用或不可写, 使得消息packet: {} 发送给用户: {} 失败!", channel.id().asShortText(), packet, to);
                SendResult sendResult = SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(new MessageException("发送消息时，通道channel：" + channel.id().asShortText() + " 不可用或不可写！")).build();
                sendCallback.onCallback(sendResult);
                MessageServerContext.publishEvent(new SendFailEvent(sendResult), true);
            }
        } catch (Exception e) {
            log.error("消息packet: {} 发送给用户: {} 失败!", packet, to);
            // 消息丢失
            SendResult sendResult =  SendResult.builder().sendStatus(SendStatusEnum.SEND_FAIL).packet(packet).exception(e).build();
            sendCallback.onCallback(sendResult);
            MessageServerContext.publishEvent(new SendFailEvent(sendResult), true);
        }
    }
}
