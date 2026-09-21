package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.executor.ThreadPoolManager;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 原文归档确认屏障：MQ 明确成功后，才允许业务提交与成功 ACK。
 * 取消/超时不撤销已发出的归档；客户端可能重试，消费者仍须按 packetId 幂等。
 */
public final class MessageArchiveHelper {
    private MessageArchiveHelper() { }

    /** 同步构包异常、异步发送失败和超时均向调用链传播；后续业务不在 MQ 回调线程运行。 */
    public static Mono<Void> confirm(Supplier<? extends CompletableFuture<?>> publish) {
        return Mono.defer(() -> Mono.fromFuture(publish.get(), true))
                .timeout(Duration.ofMillis(MessageConstant.MESSAGE_ARCHIVE_CONFIRM_TIMEOUT_MS))
                .publishOn(Schedulers.fromExecutor(ThreadPoolManager.messageProcessorExecutor()))
                .then();
    }
}
