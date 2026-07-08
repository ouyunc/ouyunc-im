package com.ouyunc.repository.support;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 特殊消息响应式编排：校验 / preparer → Redis 处理 → after；MQ 旁路异步投递。
 */
public final class ReactiveMessageOperationSupport {

    private static final Logger log = LoggerFactory.getLogger(ReactiveMessageOperationSupport.class);

    public Mono<Boolean> reactiveHandleOperation(ChannelHandlerContext ctx, Packet packet,
                                                 Mono<Boolean> validator,
                                                 Supplier<CompletableFuture<?>> mqSender,
                                                 Mono<Boolean> processor,
                                                 BiConsumer<ChannelHandlerContext, Packet> processorAfter,
                                                 Consumer<MessageEvent> exceptionConsumer,
                                                 ExceptionCodeEnum exceptionCode) {
        return validator.flatMap(valid -> {
            if (!valid) {
                exceptionConsumer.accept(new MessageEvent(ExceptionEventPayload.of(exceptionCode, null, packet), MessageEventTypeEnum.EXCEPTION));
                return Mono.just(false);
            }
            publishMqAsync(mqSender, packet, exceptionConsumer);
            return processor
                    .doOnNext(processed -> {
                        if (processed) {
                            processorAfter.accept(ctx, packet);
                        } else {
                            exceptionConsumer.accept(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.UNKNOWN_ERROR, "撤销或已读异常", packet), MessageEventTypeEnum.EXCEPTION));
                        }
                    })
                    .onErrorResume(ex -> {
                        log.error("操作处理异常 | packet={}", packet, ex);
                        exceptionConsumer.accept(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.UNKNOWN_ERROR, ex.getMessage(), packet), MessageEventTypeEnum.EXCEPTION));
                        return Mono.just(false);
                    });
        });
    }

    public <T> Mono<Boolean> reactiveHandleOperation(ChannelHandlerContext ctx, Packet packet,
                                                   Mono<T> preparer,
                                                   ExceptionCodeEnum verifyExceptionCode,
                                                   Supplier<CompletableFuture<?>> mqSender,
                                                   Function<T, Mono<Boolean>> processor,
                                                   BiConsumer<ChannelHandlerContext, Packet> processorAfter,
                                                   Consumer<MessageEvent> exceptionConsumer,
                                                   ExceptionCodeEnum processExceptionCode) {
        return preparer
                .flatMap(data -> reactiveHandleOperation(ctx, packet, Mono.just(true), mqSender,
                        processor.apply(data), processorAfter, exceptionConsumer, processExceptionCode))
                .switchIfEmpty(Mono.defer(() -> {
                    exceptionConsumer.accept(new MessageEvent(
                            ExceptionEventPayload.of(verifyExceptionCode, null, packet),
                            MessageEventTypeEnum.EXCEPTION));
                    return Mono.just(false);
                }));
    }

    private static void publishMqAsync(Supplier<CompletableFuture<?>> mqSender, Packet packet,
                                       Consumer<MessageEvent> exceptionConsumer) {
        try {
            mqSender.get().whenComplete((ignored, ex) -> {
                if (ex != null) {
                    log.warn("MQ 旁路投递失败, packetId={}, 原因: {}", packet.getPacketId(), ex.getMessage(), ex);
                    exceptionConsumer.accept(new MessageEvent(
                            ExceptionEventPayload.of(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR,
                                    "MQ 旁路投递失败: " + ex.getMessage(), packet),
                            MessageEventTypeEnum.EXCEPTION));
                }
            });
        } catch (Exception ex) {
            log.warn("MQ 旁路投递启动失败, packetId={}, 原因: {}", packet.getPacketId(), ex.getMessage(), ex);
            exceptionConsumer.accept(new MessageEvent(
                    ExceptionEventPayload.of(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR,
                            "MQ 旁路投递启动失败: " + ex.getMessage(), packet),
                    MessageEventTypeEnum.EXCEPTION));
        }
    }
}
