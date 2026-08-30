package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.SendStatusEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.SendCallback;
import com.ouyunc.base.model.SendResult;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.convert.PacketConverter;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 已知 Channel 上的 Packet 出站：协议转换、EventLoop 写出、失败回调。
 * <p>不查登录注册表、不做集群路由。按身份投递仍走 {@link MessageHelper#asyncSendMessage}。</p>
 * <p>使用场景：QoS S2C ACK / Pong 写回入站连接；查表命中后的本机写出。</p>
 */
public final class PacketChannelWriter {

    private static final Logger log = LoggerFactory.getLogger(PacketChannelWriter.class);

    private PacketChannelWriter() {
    }

    /**
     * 入站 ctx 是否可立即写出（非空、active、writable）。
     */
    public static boolean isSendable(ChannelHandlerContext ctx) {
        return ctx != null && ctx.channel() != null && ctx.channel().isActive() && ctx.channel().isWritable();
    }

    /**
     * 写回当前入站连接。ctx 不可用时返回 false，由调用方再走 {@link MessageHelper#asyncSendMessage}。
     */
    public static boolean tryReplyOnChannel(ChannelHandlerContext ctx, Packet packet) {
        if (!isSendable(ctx)) {
            return false;
        }
        sendOnChannel(ctx, packet, sendResult -> {});
        return true;
    }

    /**
     * 将 Packet 转成对端协议帧后写入入站 Channel。
     * 须先 {@link #isSendable}；转换器依赖 {@code metadata.target.protocol}，缺省时按登录信息补齐。
     */
    public static void sendOnChannel(ChannelHandlerContext ctx, Packet packet, SendCallback sendCallback) {
        if (!isSendable(ctx)) {
            notifySendFail(packet, "发送消息时，入站 ctx 不可用或不可写", sendCallback);
            return;
        }
        ensureOutboundTarget(ctx, packet);
        writeConverted(ctx.channel(), packet, sendCallback);
    }

    /**
     * 给定 Channel 做协议转换后写出（调用方已拿到连接，不再查表）。
     */
    public static void writeConverted(Channel channel, Packet packet, SendCallback sendCallback) {
        if (!validateWritable(channel, packet, sendCallback)) {
            return;
        }
        for (PacketConverter<?> packetConverter : MessageServerContext.packetConverterList) {
            Object msg = packetConverter.convertFromPacket(packet);
            if (msg == null) {
                continue;
            }
            runOnEventLoop(channel, packet, sendCallback,
                    () -> tryWriteObject(channel, msg, packet, sendCallback), null);
            return;
        }
        log.error("发送消息时，packet: {} 转换其他协议发生异常,找不到匹配的协议转换器！", packet);
        notifySendFail(packet, "发送消息时，packet转换其他协议发生异常,找不到匹配的协议转换器！", sendCallback);
    }

    /**
     * 回复目标：优先 Channel 登录身份与协议，其次报文 from + 包头协议。
     */
    public static Target resolveReplyTarget(ChannelHandlerContext ctx, Packet packet, String messageFrom) {
        Metadata metadata = packet != null && packet.getMessage() != null ? packet.getMessage().getMetadata() : null;
        LoginClientInfo loginClientInfo = ctx != null
                ? ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN)
                : null;
        if (loginClientInfo != null && StringUtils.isNotBlank(loginClientInfo.getIdentity())) {
            String appKey = StringUtils.isNotBlank(loginClientInfo.getAppKey())
                    ? loginClientInfo.getAppKey()
                    : (metadata != null ? metadata.getAppKey() : null);
            String serverAddress = StringUtils.isNotBlank(loginClientInfo.getLoginServerAddress())
                    ? loginClientInfo.getLoginServerAddress()
                    : MessageServerContext.serverProperties().getLocalServerAddress();
            return Target.newBuilder()
                    .appKey(appKey)
                    .targetIdentity(loginClientInfo.getIdentity())
                    .deviceType(loginClientInfo.getDeviceType())
                    .targetServerAddress(serverAddress)
                    .protocol(loginClientInfo.getProtocol())
                    .protocolVersion(loginClientInfo.getProtocolVersion())
                    .build();
        }
        if (packet == null || StringUtils.isBlank(messageFrom) || metadata == null) {
            return null;
        }
        return Target.newBuilder()
                .appKey(metadata.getAppKey())
                .targetIdentity(messageFrom)
                .deviceType(MessageServerContext.deviceType(metadata.getAppKey(), packet.getDeviceType()))
                .targetServerAddress(MessageServerContext.serverProperties().getLocalServerAddress())
                .protocol(packet.getProtocol())
                .protocolVersion(packet.getProtocolVersion())
                .build();
    }

    public static boolean tryWriteObject(Channel channel, Object msg, Packet packet, SendCallback sendCallback) {
        if (!validateWritable(channel, packet, sendCallback)) {
            return false;
        }
        addWriteListener(channel.writeAndFlush(msg), packet, sendCallback);
        return true;
    }

    /** 集群连接池：写出 Packet 本体，完成后归还 Channel。 */
    public static void tryWritePacketAndThen(Channel channel, Packet packet, SendCallback sendCallback, Runnable afterComplete) {
        if (!validateWritable(channel, packet, sendCallback)) {
            if (afterComplete != null) {
                afterComplete.run();
            }
            return;
        }
        ChannelFuture future = channel.writeAndFlush(packet);
        addWriteListener(future, packet, sendCallback);
        if (afterComplete != null) {
            future.addListener(f -> afterComplete.run());
        }
    }

    /**
     * 发送失败：回调 + 发布 {@link MessageEventTypeEnum#SEND_FAIL} 事件。
     * 注意：sendCallback 为空会 NPE，调用方须保证非空（与原先 MessageHelper 行为一致）。
     */
    public static void notifySendFail(Packet packet, Throwable cause, SendCallback sendCallback) {
        SendResult sendResult = SendResult.builder()
                .sendStatus(SendStatusEnum.SEND_FAIL)
                .packet(packet)
                .exception(cause)
                .build();
        sendCallback.onCallback(sendResult);
        MessageServerContext.publishEvent(new MessageEvent(sendResult, MessageEventTypeEnum.SEND_FAIL), true);
    }

    public static void notifySendFail(Packet packet, String message, SendCallback sendCallback) {
        notifySendFail(packet, new MessageException(message), sendCallback);
    }

    /**
     * 保证写出在 Channel 所属 EventLoop；loop 已关闭时执行 onLoopDead（可为 null）。
     */
    public static void runOnEventLoop(Channel channel, Packet packet, SendCallback sendCallback,
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
        notifySendFail(packet, "发送消息时，channel.eventLoop 被终止或关闭！", sendCallback);
    }

    static void ensureOutboundTarget(ChannelHandlerContext ctx, Packet packet) {
        if (packet == null || packet.getMessage() == null || packet.getMessage().getMetadata() == null) {
            return;
        }
        Metadata metadata = packet.getMessage().getMetadata();
        if (metadata.getTarget() != null) {
            return;
        }
        Target target = resolveReplyTarget(ctx, packet, packet.getMessage().getTo());
        if (target != null) {
            metadata.setTarget(target);
        }
    }

    private static boolean validateWritable(Channel channel, Packet packet, SendCallback sendCallback) {
        if (channel == null) {
            notifySendFail(packet, "channel 为空，无法写入", sendCallback);
            return false;
        }
        if (!channel.isActive()) {
            notifySendFail(packet, "channel 未激活，无法写入", sendCallback);
            return false;
        }
        if (!channel.isWritable()) {
            log.warn("channel 不可写，丢弃或等待上层重试: {}", channel);
            notifySendFail(packet, "channel 当前不可写", sendCallback);
            return false;
        }
        return true;
    }

    private static void addWriteListener(ChannelFuture future, Packet packet, SendCallback sendCallback) {
        future.addListener((ChannelFutureListener) f -> {
            if (f.isSuccess()) {
                sendCallback.onCallback(SendResult.builder().sendStatus(SendStatusEnum.SEND_OK).packet(packet).build());
            } else {
                notifySendFail(packet, f.cause(), sendCallback);
            }
        });
    }
}
