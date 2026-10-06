package com.ouyunc.message.protocol;


import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;
import com.ouyunc.base.constant.enums.SendStatusEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.Protocol;
import com.ouyunc.base.model.SendCallback;
import com.ouyunc.base.model.SendResult;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.message.convert.BinaryWebSocketFramePacketConverter;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.handler.*;
import com.ouyunc.message.http.HttpRequestDispatcher;
import com.ouyunc.message.schedule.QosRetryScheduler;
import com.ouyunc.message.support.WsHandshakeSupport;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

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

        @Override
        public void doSendMessage(Channel channel, Packet packet, SendCallback sendCallback) {
            Object frame = null;
            try {
                if (packet != null && packet.getMessage() != null) {
                    frame = BinaryWebSocketFramePacketConverter.INSTANCE.convertFromPacket(this, packet);
                }
            } catch (RuntimeException e) {
                log.error("WebSocket 出站编码失败 packetId={}", packet == null ? null : packet.getPacketId(), e);
                callback(sendCallback, packet, SendStatusEnum.SEND_FAIL, e);
                return;
            }
            flushSafely(channel, frame, packet, sendCallback);
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

        @Override
        public void doSendMessage(Channel channel, Packet packet, SendCallback sendCallback) {
            flushSafely(channel, null, packet, sendCallback);
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

        @Override
        public void doSendMessage(Channel channel, Packet packet, SendCallback sendCallback) {
            flushSafely(channel, packet, packet, sendCallback);
        }
    },

    /**
     * 外部客户端原生 Packet：编解码复用 Packet，业务路径与 WS 一致（client 三阶段 + 登录鉴权），
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

        @Override
        public void doSendMessage(Channel channel, Packet packet, SendCallback sendCallback) {
            Object frame = null;
            try {
                if (packet != null && packet.getMessage() != null) {
                    frame = packet.copyForExternalDelivery();
                }
            } catch (RuntimeException e) {
                log.error("客户端协议出站编码失败 packetId={}", packet == null ? null : packet.getPacketId(), e);
                callback(sendCallback, packet, SendStatusEnum.SEND_FAIL, e);
                return;
            }
            flushSafely(channel, frame, packet, sendCallback);
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

    @Override
    public void doSendMessage(Channel channel, Packet packet, SendCallback sendCallback) {
        flushSafely(channel, null, packet, sendCallback);
    }

    /** 编码异常也回调失败，调用方据此归还集群连接。 */
    private static void flushSafely(Channel channel, Object msg, Packet packet, SendCallback sendCallback) {
        try {
            flush(channel, msg, packet, sendCallback);
        } catch (RuntimeException e) {
            log.error("发送消息时，协议写出失败 packetId={}", packet == null ? null : packet.getPacketId(), e);
            ReferenceCountUtil.release(msg);
            callback(sendCallback, packet, SendStatusEnum.SEND_FAIL, e);
        }
    }

    /** 各协议编出帧之后共用的写出：EventLoop、水位重试。成败只走回调。 */
    private static void flush(Channel channel, Object msg, Packet packet, SendCallback sendCallback) {
        if (channel == null || !channel.isActive()) {
            // 编码产物尚未交给 Netty，连接失效时仍由当前方法负责释放，避免 WebSocket ByteBuf 泄漏。
            ReferenceCountUtil.release(msg);
            callback(sendCallback, packet, SendStatusEnum.SEND_FAIL, new MessageException(describeUnwritable(channel)));
            return;
        }
        if (msg == null) {
            callback(sendCallback, packet, SendStatusEnum.SEND_FAIL, new MessageException("发送消息时，当前协议无法编码该包"));
            return;
        }
        runOnEventLoop(channel, packet, sendCallback,
                () -> writeOrRelease(channel, msg, packet, sendCallback, 0),
                () -> ReferenceCountUtil.release(msg));
    }

    private static void writeOrRelease(Channel channel, Object msg, Packet packet, SendCallback sendCallback, int attempt) {
        if (channel == null || !channel.isActive()) {
            ReferenceCountUtil.release(msg);
            callback(sendCallback, packet, SendStatusEnum.SEND_FAIL, new MessageException(describeUnwritable(channel)));
            return;
        }
        if (!channel.isWritable()) {
            scheduleWritableRetry(channel, packet, sendCallback, attempt,
                    () -> writeOrRelease(channel, msg, packet, sendCallback, attempt + 1),
                    () -> ReferenceCountUtil.release(msg));
            return;
        }
        channel.writeAndFlush(msg).addListener((ChannelFutureListener) f -> {
            if (f.isSuccess()) {
                QosRetryScheduler.rememberOutbound(packet);
                callback(sendCallback, packet, SendStatusEnum.SEND_OK, null);
            } else {
                callback(sendCallback, packet, SendStatusEnum.SEND_FAIL, f.cause());
            }
        });
    }

    private static void callback(SendCallback sendCallback, Packet packet, SendStatusEnum status, Throwable cause) {
        if (sendCallback == null) {
            return;
        }
        sendCallback.onCallback(SendResult.builder()
                .sendStatus(status)
                .packet(packet)
                .exception(cause)
                .build());
    }

    public static void notifySendFail(Packet packet, Throwable cause, SendCallback sendCallback) {
        SendResult sendResult = SendResult.builder()
                .sendStatus(SendStatusEnum.SEND_FAIL)
                .packet(packet)
                .exception(cause)
                .build();
        if (sendCallback != null) {
            sendCallback.onCallback(sendResult);
        }
        MessageServerContext.publishEvent(new MessageEvent(sendResult, MessageEventTypeEnum.SEND_FAIL), true);
    }

    public static void notifySendFail(Packet packet, String message, SendCallback sendCallback) {
        notifySendFail(packet, new MessageException(message), sendCallback);
    }

    private static void runOnEventLoop(Channel channel, Packet packet, SendCallback sendCallback,
                                       Runnable task, Runnable onLoopDead) {
        EventLoop eventLoop = channel.eventLoop();
        if (eventLoop.inEventLoop()) {
            task.run();
            return;
        }
        if (!eventLoop.isTerminated() && !eventLoop.isShutdown() && !eventLoop.isShuttingDown()) {
            eventLoop.execute(task);
            return;
        }
        if (onLoopDead != null) {
            onLoopDead.run();
        }
        log.error("发送消息时，channel.eventLoop 被终止或关闭； channelId: {}", channel.id().asShortText());
        callback(sendCallback, packet, SendStatusEnum.SEND_FAIL,
                new MessageException("发送消息时，channel.eventLoop 被终止或关闭！"));
    }

    private static void scheduleWritableRetry(Channel channel, Packet packet, SendCallback sendCallback, int attempt,
                                              Runnable retryAction, Runnable onGiveUp) {
        if (attempt >= MessageConstant.CHANNEL_WRITE_RETRY_MAX_ATTEMPTS) {
            log.warn("channel 不可写重试耗尽 channelId={} attempts={} packetId={}",
                    channel != null ? channel.id().asShortText() : "null",
                    attempt,
                    packet != null ? packet.getPacketId() : -1L);
            if (onGiveUp != null) {
                onGiveUp.run();
            }
            callback(sendCallback, packet, SendStatusEnum.SEND_FAIL, new MessageException(describeUnwritable(channel)));
            return;
        }
        EventLoop eventLoop = channel.eventLoop();
        if (eventLoop.isTerminated() || eventLoop.isShutdown() || eventLoop.isShuttingDown()) {
            if (onGiveUp != null) {
                onGiveUp.run();
            }
            callback(sendCallback, packet, SendStatusEnum.SEND_FAIL,
                    new MessageException("发送消息时，channel.eventLoop 被终止或关闭！"));
            return;
        }
        long delayMs = MessageConstant.CHANNEL_WRITE_RETRY_BASE_DELAY_MS * (attempt + 1L);
        eventLoop.schedule(() -> {
            if (!channel.isActive()) {
                if (onGiveUp != null) {
                    onGiveUp.run();
                }
                callback(sendCallback, packet, SendStatusEnum.SEND_FAIL, new MessageException(describeUnwritable(channel)));
                return;
            }
            retryAction.run();
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private static String describeUnwritable(Channel channel) {
        if (channel == null) {
            return "channel 为空，无法写入";
        }
        if (!channel.isActive()) {
            return "channel 未激活，无法写入";
        }
        return "channel 当前不可写（重试耗尽）";
    }

}
