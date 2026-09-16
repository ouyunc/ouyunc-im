package com.ouyunc.message.handler;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 登录超时：未打上 {@code TAG_LOGIN} 则关连；{@code IN_FLIGHT} 时按次数续期，避免 bind 卡住占坑。
 */
public final class LoginTimeoutSupport {

    private static final Logger log = LoggerFactory.getLogger(LoginTimeoutSupport.class);

    private LoginTimeoutSupport() {
    }

    /**
     * 在当前连接上启动登录超时。
     */
    public static void install(ChannelHandlerContext ctx) {
        cancel(ctx);
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_TIMEOUT_STRIKE, 0);
        schedule(ctx);
    }

    /**
     * 取消超时任务。
     *
     * @return true 表示确有任务被取消
     */
    public static boolean cancel(ChannelHandlerContext ctx) {
        AttributeKey<ScheduledFuture<?>> key =
                AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_LOGIN_TIMEOUT_SCHEDULED_FUTURE);
        ScheduledFuture<?> timeoutFuture = ctx.channel().attr(key).get();
        boolean cancelled = false;
        if (timeoutFuture != null) {
            cancelled = timeoutFuture.cancel(false);
            ctx.channel().attr(key).set(null);
        }
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_TIMEOUT_STRIKE, null);
        return cancelled;
    }

    private static void schedule(ChannelHandlerContext ctx) {
        int timeoutSec = MessageServerContext.serverProperties().getServerLoginTimeout();
        if (timeoutSec <= 0) {
            return;
        }
        ScheduledFuture<?> timeoutFuture = ctx.executor().schedule(() -> onTimeout(ctx), timeoutSec, TimeUnit.SECONDS);
        ctx.channel().attr(AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_LOGIN_TIMEOUT_SCHEDULED_FUTURE))
                .set(timeoutFuture);
    }

    private static void onTimeout(ChannelHandlerContext ctx) {
        if (!ctx.channel().isActive()) {
            return;
        }
        LoginClientInfo loginInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        if (loginInfo != null) {
            return;
        }
        if (Boolean.TRUE.equals(ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_IN_FLIGHT))) {
            Integer strike = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_TIMEOUT_STRIKE);
            int n = strike == null ? 0 : strike;
            if (n < MessageConstant.LOGIN_TIMEOUT_IN_FLIGHT_MAX_RETRY) {
                ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_LOGIN_TIMEOUT_STRIKE, n + 1);
                log.warn("登录进行中，登录超时续期 strike={} channelId={}", n + 1, ctx.channel().id().asShortText());
                schedule(ctx);
                return;
            }
        }
        log.error("登录超时, 在规定时间：{} s 内未完成登录，关闭连接: {}!",
                MessageServerContext.serverProperties().getServerLoginTimeout(), ctx.channel().id().asShortText());
        ctx.close();
    }
}
