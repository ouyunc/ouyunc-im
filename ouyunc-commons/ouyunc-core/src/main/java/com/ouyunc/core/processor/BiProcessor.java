package com.ouyunc.core.processor;

/**
 * 双参处理器：入参 {@code C}/{@code I}，返回值 {@code R} 由实现方决定。
 * <p>消息侧常见 {@code R = Mono<Void>}；亦可返回同步结果或其它异步类型。</p>
 *
 * @param <C> 上下文（如 {@code ChannelHandlerContext}）
 * @param <I> 入参（如 {@code Packet}）
 * @param <R> 返回值（如 {@code Mono<Void>}、{@code Boolean} 等）
 */
@FunctionalInterface
public interface BiProcessor<C, I, R> {

    /**
     * 核心业务处理。
     *
     * @param context 上下文
     * @param input   业务入参
     * @return 处理结果，类型由泛型 {@code R} 指定
     */
    R process(C context, I input);
}
