
package com.ouyunc.message.validator;
import io.netty.channel.ChannelHandlerContext;
import java.util.Objects;

/**
 * @Author fzx
 * @Description: 校验器
 */
@FunctionalInterface
public interface Validator<T> {


    boolean verify(T t, ChannelHandlerContext ctx);

    default Validator<T> and(Validator<? super T> other) {
        Objects.requireNonNull(other);
        return (t, ctx) -> verify(t,ctx) && other.verify(t,ctx);
    }


    default Validator<T> negate() {
        return (t, ctx) -> !verify(t, ctx);
    }


    default Validator<T> or(Validator<? super T> other) {
        Objects.requireNonNull(other);
        return (t, ctx) -> verify(t,ctx) || other.verify(t, ctx);
    }



    @SuppressWarnings("unchecked")
    static <T> Validator<T> not(Validator<? super T> target) {
        Objects.requireNonNull(target);
        return (Validator<T>)target.negate();
    }
}
