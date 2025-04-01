package com.ouyunc.message.validator;

import io.netty.channel.ChannelHandlerContext;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * @Author fzx
 * @Description: 响应式校验器
 */
@FunctionalInterface
public interface ReactiveValidator<T> {

    /**
     * 响应式验证方法，返回 Mono<Boolean>
     * @param t 要验证的对象
     * @param ctx 通道处理上下文
     * @return 包含验证结果的 Mono
     */
    Mono<Boolean> verify(T t, ChannelHandlerContext ctx);

    /**
     * 组合两个验证器，使用逻辑与操作
     * @param other 另一个验证器
     * @return 组合后的验证器
     */
    default ReactiveValidator<T> and(ReactiveValidator<? super T> other) {
        Objects.requireNonNull(other);
        return (t, ctx) -> this.verify(t, ctx)
                .flatMap(result1 -> other.verify(t, ctx)
                        .map(result2 -> result1 && result2));
    }

    /**
     * 对验证结果取反
     * @return 取反后的验证器
     */
    default ReactiveValidator<T> negate() {
        return (t, ctx) -> this.verify(t, ctx).map(result -> !result);
    }

    /**
     * 组合两个验证器，使用逻辑或操作
     * @param other 另一个验证器
     * @return 组合后的验证器
     */
    default ReactiveValidator<T> or(ReactiveValidator<? super T> other) {
        Objects.requireNonNull(other);
        return (t, ctx) -> this.verify(t, ctx)
                .flatMap(result1 -> {
                    if (result1) {
                        return Mono.just(true);
                    }
                    return other.verify(t, ctx);
                });
    }

    /**
     * 静态方法，对验证器的结果取反
     * @param target 目标验证器
     * @param <T> 验证对象的类型
     * @return 取反后的验证器
     */
    @SuppressWarnings("unchecked")
    static <T> ReactiveValidator<T> not(ReactiveValidator<? super T> target) {
        Objects.requireNonNull(target);
        return (ReactiveValidator<T>) target.negate();
    }
}