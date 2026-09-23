package com.ouyunc.repository.support;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.exception.ExceptionReporter;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 特殊消息响应式编排：校验 / preparer → MQ 确认 → Redis 处理成功 → ACK/投递。
 * MQ 失败不写 Redis、不 ACK，交给客户端重试。
 */
public final class ReactiveMessageOperationSupport {

    private static final Logger log = LoggerFactory.getLogger(ReactiveMessageOperationSupport.class);

    private static final String SCENE = "ReactiveMessageOperationSupport.reactiveHandleOperation";

    public Mono<Boolean> reactiveHandleOperation(ChannelHandlerContext ctx, Packet packet,
                                                 Mono<Boolean> validator,
                                                 String mqTopic, String mqKey,
                                                 Mono<Boolean> processor,
                                                 BiConsumer<ChannelHandlerContext, Packet> processorAfter,
                                                 ExceptionCodeEnum exceptionCode) {
        return validator.flatMap(valid -> {
            if (!valid) {
                reportVerifyOrProcess(exceptionCode, packet);
                return Mono.just(false);
            }
            return RepositorySupports.MQ.confirmPacket(mqTopic, mqKey, packet)
                    .then(processor)
                    .doOnNext(processed -> {
                        if (processed) {
                            try {
                                processorAfter.accept(ctx, packet);
                            } catch (Exception callbackError) {
                                // 主操作已经成功，后续通知失败不可把受理状态回滚为失败。
                                log.error("操作已提交，但后续通知失败, messageId={}",
                                        packet.getMessage().getId(), callbackError);
                            }
                        } else {
                            ExceptionReporter.reportSystem(ExceptionCodeEnum.UNKNOWN_ERROR, "撤销或已读异常", SCENE, packet);
                        }
                    })
                    .onErrorResume(ex -> {
                        log.error("操作处理异常 | packet={}", packet, ex);
                        ExceptionReporter.reportSystem(ExceptionCodeEnum.UNKNOWN_ERROR, ex.getMessage(), SCENE, packet, ex);
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
                                                   ExceptionCodeEnum processExceptionCode) {
        return preparer
                .flatMap(data -> reactiveHandleOperation(ctx, packet, Mono.just(true), mqTopic, mqKey,
                        processor.apply(data), processorAfter, processExceptionCode))
                .switchIfEmpty(Mono.defer(() -> {
                    ExceptionReporter.reportBusiness(verifyExceptionCode, null, SCENE, packet);
                    return Mono.just(false);
                }));
    }

    /**
     * 首参 overload 的 exceptionCode 既可能是校验码（业务）也可能是处理码（系统）。
     */
    private static void reportVerifyOrProcess(ExceptionCodeEnum code, Packet packet) {
        if (code == null) {
            ExceptionReporter.reportSystem(ExceptionCodeEnum.UNKNOWN_ERROR, null, SCENE, packet);
            return;
        }
        String name = code.name();
        if (name.endsWith("_VERIFY_ERROR") || name.contains("VERIFY")) {
            ExceptionReporter.reportBusiness(code, null, SCENE, packet);
        } else {
            ExceptionReporter.reportSystem(code, null, SCENE, packet);
        }
    }
}
