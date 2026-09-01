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

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 特殊消息响应式编排：校验 / preparer → Redis 处理 → after；MQ 旁路异步投递。
 */
public final class ReactiveMessageOperationSupport {

    private static final Logger log = LoggerFactory.getLogger(ReactiveMessageOperationSupport.class);

    public Mono<Boolean> reactiveHandleOperation(ChannelHandlerContext ctx, Packet packet,
                                                 Mono<Boolean> validator,
                                                 String mqTopic, String mqKey,
                                                 Mono<Boolean> processor,
                                                 BiConsumer<ChannelHandlerContext, Packet> processorAfter,
                                                 Consumer<MessageEvent> exceptionConsumer,
                                                 ExceptionCodeEnum exceptionCode) {
        return validator.flatMap(valid -> {
            if (!valid) {
                exceptionConsumer.accept(new MessageEvent(ExceptionEventPayload.of(exceptionCode, null, packet), MessageEventTypeEnum.EXCEPTION));
                return Mono.just(false);
            }
            RepositorySupports.MQ.publishPacketAsync(mqTopic, mqKey, packet, "MQ 旁路投递 topic=" + mqTopic);
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
                                                   String mqTopic, String mqKey,
                                                   Function<T, Mono<Boolean>> processor,
                                                   BiConsumer<ChannelHandlerContext, Packet> processorAfter,
                                                   Consumer<MessageEvent> exceptionConsumer,
                                                   ExceptionCodeEnum processExceptionCode) {
        return preparer
                .flatMap(data -> reactiveHandleOperation(ctx, packet, Mono.just(true), mqTopic, mqKey,
                        processor.apply(data), processorAfter, exceptionConsumer, processExceptionCode))
                .switchIfEmpty(Mono.defer(() -> {
                    exceptionConsumer.accept(new MessageEvent(
                            ExceptionEventPayload.of(verifyExceptionCode, null, packet),
                            MessageEventTypeEnum.EXCEPTION));
                    return Mono.just(false);
                }));
    }
}
