package com.ouyunc.message.handler;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ChannelOrderedTasks;
import com.ouyunc.message.processor.AbstractMessageBiProcessor;
import com.ouyunc.message.validator.DeviceValidator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletionStage;

/**
 * 统一 Packet 业务入口。
 * <ul>
 *   <li>{@link Mode#CLIENT}：设备校验后 {@code preProcess → process → postProcess}；
 *       外部心跳 {@link MessageTypeEnum#PING_PONG} 不入有序队列</li>
 *   <li>{@link Mode#CLUSTER}：仅 {@code process → postProcess}；
 *       {@link OuyuncMessageTypeEnum#SYN_ACK} / {@link OuyuncMessageTypeEnum#RELATION_CACHE_INVALIDATE}
 *       不入有序队列</li>
 * </ul>
 */
public class PacketHandler extends SimpleChannelInboundHandler<Packet> {

    private static final Logger log = LoggerFactory.getLogger(PacketHandler.class);

    /** 入站模式：客户端完整三阶段 / 集群仅 process+post。 */
    public enum Mode {
        CLIENT,
        CLUSTER
    }

    private final Mode mode;

    public PacketHandler(Mode mode) {
        this.mode = mode == null ? Mode.CLIENT : mode;
    }

    /** 客户端 WS/MQTT 入口。 */
    public static PacketHandler client() {
        return new PacketHandler(Mode.CLIENT);
    }

    /** 集群 OUYUNC 入口。 */
    public static PacketHandler cluster() {
        return new PacketHandler(Mode.CLUSTER);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        if (mode == Mode.CLIENT) {
            handleClient(ctx, packet);
        } else {
            handleCluster(ctx, packet);
        }
    }

    private void handleClient(ChannelHandlerContext ctx, Packet packet) {
        if (DeviceValidator.INSTANCE.negate().verify(packet, ctx)) {
            log.error("设备类型不支持，deviceType= {}, appKey:{}", packet.getDeviceType(),
                    packet.getMessage().getMetadata().getAppKey());
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(
                    ExceptionCodeEnum.ILLEGAL_DEVICE_TYPE_ERROR, null, packet),
                    MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return;
        }
        AbstractMessageBiProcessor<? extends Number> processor = resolveProcessor(ctx, packet);
        if (processor == null) {
            return;
        }
        if (!MessageServerContext.serverProperties().isClientHeartBeatEnable()
                && packet.getMessageType() == MessageTypeEnum.PING_PONG.getType()) {
            log.error("外部客户端未开启心跳, 非法消息类型，messageType= {}", packet.getMessageType());
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(
                    ExceptionCodeEnum.ILLEGAL_MESSAGE_TYPE_ERROR, "外部客户端未开启心跳, 非法消息类型", packet),
                    MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return;
        }
        // 外部客户端心跳：EventLoop 直接 process，不占业务有序队列
        if (packet.getMessageType() == MessageTypeEnum.PING_PONG.getType()) {
            runLightProcess(ctx, packet, processor, "客户端心跳");
            return;
        }
        ChannelOrderedTasks.executeAsync(ctx.channel(),
                () -> invokeFull(ctx, packet, processor));
    }

    private void handleCluster(ChannelHandlerContext ctx, Packet packet) {
        AbstractMessageBiProcessor<? extends Number> processor = resolveProcessor(ctx, packet);
        if (processor == null) {
            return;
        }
        // 集群心跳 / 本机关系失效：轻量处理，不占业务有序队列
        if (packet.getMessageType() == OuyuncMessageTypeEnum.SYN_ACK.getType()
                || packet.getMessageType() == OuyuncMessageTypeEnum.RELATION_CACHE_INVALIDATE.getType()) {
            String scene = packet.getMessageType() == OuyuncMessageTypeEnum.SYN_ACK.getType()
                    ? "集群 SYN/ACK" : "关系缓存失效";
            runLightProcess(ctx, packet, processor, scene);
            return;
        }
        ChannelOrderedTasks.executeAsync(ctx.channel(),
                () -> invokeProcessPost(ctx, packet, processor));
    }

    private AbstractMessageBiProcessor<? extends Number> resolveProcessor(ChannelHandlerContext ctx, Packet packet) {
        AbstractMessageBiProcessor<? extends Number> processor =
                MessageServerContext.messageProcessorCache.get(packet.getMessageType());
        if (processor == null) {
            log.error("非法消息类型，messageType= {}", packet.getMessageType());
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(
                    ExceptionCodeEnum.ILLEGAL_MESSAGE_TYPE_ERROR, "非法消息类型", packet),
                    MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
        }
        return processor;
    }

    /**
     * 客户端完整三阶段：preProcess → process → postProcess。
     * pre 返回 false/empty 时跳过后续阶段。
     */
    private static CompletionStage<Void> invokeFull(ChannelHandlerContext ctx, Packet packet,
                                                    AbstractMessageBiProcessor<? extends Number> processor) {
        Mono<Void> chain = processor.preProcess(ctx, packet)
                .flatMap(passed -> {
                    if (!Boolean.TRUE.equals(passed)) {
                        return Mono.empty();
                    }
                    return processor.process(ctx, packet)
                            .then(Mono.defer(() -> processor.postProcess(ctx, packet)));
                })
                .onErrorResume(error -> {
                    // 吞掉 Mono 错误以免打乱有序队列；异常统一交给尾部 ExceptionHandler
                    fireUnifiedException(ctx, packet, error, "消息三阶段执行异常");
                    return Mono.empty();
                });
        return ChannelOrderedTasks.toVoidStage(chain);
    }

    /** 集群等无 pre 路径：仅 process → postProcess。 */
    private static CompletionStage<Void> invokeProcessPost(ChannelHandlerContext ctx, Packet packet,
                                                           AbstractMessageBiProcessor<? extends Number> processor) {
        Mono<Void> chain = processor.process(ctx, packet)
                .then(Mono.defer(() -> processor.postProcess(ctx, packet)))
                .onErrorResume(error -> {
                    fireUnifiedException(ctx, packet, error, "消息 process/post 执行异常");
                    return Mono.empty();
                });
        return ChannelOrderedTasks.toVoidStage(chain);
    }

    /** 心跳类轻量处理：不入有序队列，避免被业务堵住。 */
    private void runLightProcess(ChannelHandlerContext ctx, Packet packet,
                                 AbstractMessageBiProcessor<? extends Number> processor,
                                 String scene) {
        processor.process(ctx, packet).subscribe(
                unused -> { },
                e -> fireUnifiedException(ctx, packet, e, scene + " process 异常"));
    }

    /**
     * 异步业务异常回流 Netty 管道尾部 {@link ExceptionHandler}。
     * <p>有序任务可能在虚拟线程执行，必须切回 EventLoop 再 {@code fireExceptionCaught}。</p>
     */
    private static void fireUnifiedException(ChannelHandlerContext ctx, Packet packet,
                                             Throwable error, String scene) {
        log.error("{}, packetId={}", scene, packet == null ? null : packet.getPacketId(), error);
        if (ctx == null || error == null) {
            return;
        }
        Runnable fire = () -> {
            try {
                ctx.fireExceptionCaught(error);
            } catch (Exception ex) {
                // 兜底：管道已拆时仍走与 ExceptionHandler 相同的事件发布
                log.error("fireExceptionCaught 失败，直接发布异常事件 packetId={}",
                        packet == null ? null : packet.getPacketId(), ex);
                MessageServerContext.publishEvent(new MessageEvent(error, MessageEventTypeEnum.EXCEPTION), true);
            }
        };
        if (ctx.channel() == null) {
            MessageServerContext.publishEvent(new MessageEvent(error, MessageEventTypeEnum.EXCEPTION), true);
            return;
        }
        var eventLoop = ctx.channel().eventLoop();
        if (eventLoop.inEventLoop()) {
            fire.run();
            return;
        }
        if (eventLoop.isShuttingDown() || eventLoop.isShutdown() || eventLoop.isTerminated()) {
            MessageServerContext.publishEvent(new MessageEvent(error, MessageEventTypeEnum.EXCEPTION), true);
            return;
        }
        eventLoop.execute(fire);
    }
}
