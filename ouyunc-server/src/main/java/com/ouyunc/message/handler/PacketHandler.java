package com.ouyunc.message.handler;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.MqttMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageTypeEnum;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ChannelOrderedTasks;
import com.ouyunc.message.processor.AbstractMessageBiProcessor;
import com.ouyunc.message.safety.ContentSafetyIngress;
import com.ouyunc.message.validator.DeviceValidator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 统一 Packet 业务入口。
 * <ul>
 *   <li>{@link Mode#CLIENT}：{@code preProcess（含 DUP 展开）→ 内容安全 → process → postProcess}（同一条有序任务）；
 *       外部心跳 / MQTT PINGREQ 不入有序队列、不做敏感词</li>
 *   <li>{@link Mode#CLUSTER}：仅 {@code process → postProcess}；
 *       {@link OuyuncMessageTypeEnum#SYN_ACK}、{@link OuyuncMessageTypeEnum#QOS_RETRY_CANCEL} 不入有序队列</li>
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
        // 外部心跳 / MQTT PINGREQ：EventLoop 直接 process，不占业务有序队列、不过敏感词
        if (packet.getMessageType() == MessageTypeEnum.PING_PONG.getType() || isMqttPingReq(packet)) {
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
        // 集群心跳 / 取消 QoS 重试：轻量处理，不占业务有序队列
        if (packet.getMessageType() == OuyuncMessageTypeEnum.SYN_ACK.getType()
                || packet.getMessageType() == OuyuncMessageTypeEnum.QOS_RETRY_CANCEL.getType()) {
            String scene = packet.getMessageType() == OuyuncMessageTypeEnum.SYN_ACK.getType()
                    ? "集群 SYN/ACK" : "集群 QOS_RETRY_CANCEL";
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
     * 客户端完整三阶段：设备校验 → preProcess（含 DUP 展开）→ 内容安全 → process → postProcess。
     * 安全检查与业务必须同一条有序任务，禁止拆成两次入队（否则 MASK 与 process 可能被后到的包插队）。
     * pre 返回 false/empty 时跳过后续阶段。超时取消时释放尚未 commit 的 QoS 占位。
     */
    private static CompletionStage<Void> invokeFull(ChannelHandlerContext ctx, Packet packet,
                                                    AbstractMessageBiProcessor<? extends Number> processor) {
        if (!deviceAllowedOnWorker(ctx, packet)) {
            log.error("设备类型不支持，deviceType= {}, appKey:{}", packet.getDeviceType(),
                    packet.getMessage() == null ? null : packet.getMessage().getMetadata().getAppKey());
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(
                    ExceptionCodeEnum.ILLEGAL_DEVICE_TYPE_ERROR, null, packet),
                    MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return CompletableFuture.completedFuture(null);
        }
        Mono<Void> chain = processor.preProcess(ctx, packet)
                .flatMap(passed -> {
                    if (!Boolean.TRUE.equals(passed)) {
                        return Mono.empty();
                    }
                    if (!ContentSafetyIngress.applyOnWorker(ctx, packet)) {
                        return Mono.empty();
                    }
                    return processor.process(ctx, packet)
                            .then(Mono.defer(() -> processor.postProcess(ctx, packet)));
                })
                .doOnCancel(() -> releaseQosOnCancel(packet, processor))
                .onErrorResume(error -> {
                    // 吞掉 Mono 错误以免打乱有序队列；业务异常不断连
                    publishBusinessException(packet, error, "消息三阶段执行异常");
                    return Mono.empty();
                });
        return ChannelOrderedTasks.toVoidStage(chain);
    }

    /** 集群等无 pre 路径：仅 process → postProcess。 */
    private static CompletionStage<Void> invokeProcessPost(ChannelHandlerContext ctx, Packet packet,
                                                           AbstractMessageBiProcessor<? extends Number> processor) {
        Mono<Void> chain = processor.process(ctx, packet)
                .then(Mono.defer(() -> processor.postProcess(ctx, packet)))
                .doOnCancel(() -> releaseQosOnCancel(packet, processor))
                .onErrorResume(error -> {
                    publishBusinessException(packet, error, "消息 process/post 执行异常");
                    return Mono.empty();
                });
        return ChannelOrderedTasks.toVoidStage(chain);
    }

    /**
     * 登录后只比对 Channel 登录设备类型，避免每包打 Redis；未登录才走白名单。
     */
    private static boolean deviceAllowedOnWorker(ChannelHandlerContext ctx, Packet packet) {
        LoginClientInfo login = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        if (login != null) {
            return login.getDeviceType() == packet.getDeviceType();
        }
        return DeviceValidator.INSTANCE.verify(packet, ctx);
    }

    private static boolean isMqttPingReq(Packet packet) {
        return packet.getProtocol() == ProtocolTypeEnum.MQTT.getProtocol()
                && packet.getMessage() != null
                && packet.getMessage().getContentType() == MqttMessageContentTypeEnum.MQTT_PINGREQ.getType();
    }

    private static void releaseQosOnCancel(Packet packet, AbstractMessageBiProcessor<? extends Number> processor) {
        if (!MessageContext.isQosEnable() || packet == null || processor == null) {
            return;
        }
        processor.repository().releaseQosClaim(packet);
    }

    /** 心跳类轻量处理：不入有序队列，避免被业务堵住。 */
    private void runLightProcess(ChannelHandlerContext ctx, Packet packet,
                                 AbstractMessageBiProcessor<? extends Number> processor,
                                 String scene) {
        processor.process(ctx, packet).subscribe(
                unused -> { },
                e -> publishBusinessException(packet, e, scene + " process 异常"));
    }

    /**
     * 业务 process 失败只记日志和事件，不断开连接。
     * <p>管道损坏（解码/SSL/IO）才走尾部 {@link ExceptionHandler} 关通道。</p>
     */
    private static void publishBusinessException(Packet packet, Throwable error, String scene) {
        log.error("{}, packetId={}", scene, packet == null ? null : packet.getPacketId(), error);
        if (error == null) {
            return;
        }
        MessageServerContext.publishEvent(new MessageEvent(error, MessageEventTypeEnum.EXCEPTION), true);
    }
}
