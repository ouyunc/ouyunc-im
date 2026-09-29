package com.ouyunc.message.helper;

import com.ouyunc.base.constant.IdleNotifyConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.LoginScopeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.NetworkEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.SendCallback;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.ServerNotifyContent;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.idle.IdleNotifyTextCache;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 业务读空闲：按登录 scope 向本连接下行 {@link MessageTypeEnum#SERVER_NOTIFY}。
 * 文案优先租户 Redis Hash + 本机 Caffeine，其次全平台 Hash，最后内置兜底（仅 strike 1/2）。
 */
public final class BusinessIdleNotifyHelper {

    private static final Logger log = LoggerFactory.getLogger(BusinessIdleNotifyHelper.class);

    private BusinessIdleNotifyHelper() {
    }

    /**
     * 按 strike 向触发空闲的本连接发送提示。
     *
     * @param strike 连续业务空闲次数（1=首次提示，2+=预断开/重复提示，文案按 Redis field 可扩展）
     */
    public static void notifyIdle(ChannelHandlerContext ctx, LoginClientInfo loginInfo, int strike) {
        if (ctx == null || loginInfo == null || strike <= NumberConstant.NUMBER_0) {
            return;
        }
        boolean closeAfter = shouldCloseAfterStrike(loginInfo, strike);
        String text = resolveNotifyText(loginInfo, strike);
        if (text == null) {
            if (closeAfter) {
                closeIfStillDue(ctx);
            }
            return;
        }
        ctx.channel().eventLoop().execute(() -> {
            if (!ctx.channel().isActive()) {
                return;
            }
            // 组包前已有业务上行：strike 已清零，提示作废，关连一并取消
            if (isIdleStrikeCleared(ctx)) {
                return;
            }
            try {
                Packet packet = buildNotifyPacket(loginInfo, text);
                SendCallback afterSend = closeAfter
                        ? unused -> closeIfStillDue(ctx)
                        : unused -> { };
                MessageSender.sendControl(ctx, packet, afterSend);
            } catch (Exception e) {
                log.warn("业务空闲提示下发失败, appKey={}, identity={}, strike={}: {}",
                        loginInfo.getAppKey(), loginInfo.getIdentity(), strike, e.getMessage());
                if (closeAfter) {
                    closeIfStillDue(ctx);
                }
            }
        });
    }

    /** 登录配置了关连档且当前次数已达到。 */
    static boolean shouldCloseAfterStrike(LoginClientInfo loginInfo, int strike) {
        int closeAt = loginInfo.getBusinessIdleCloseStrike();
        return closeAt > NumberConstant.NUMBER_0 && strike >= closeAt;
    }

    private static void closeIfStillDue(ChannelHandlerContext ctx) {
        if (ctx == null || ctx.channel() == null) {
            return;
        }
        Runnable close = () -> {
            if (isCloseStillDue(ctx)) {
                ctx.close();
            }
        };
        if (ctx.channel().eventLoop().inEventLoop()) {
            close.run();
        } else {
            ctx.channel().eventLoop().execute(close);
        }
    }

    /**
     * 解析下行文案。{@code pre-close} 支持 {@code %d}/{@code %s} 填 {@code businessIdleSeconds}。
     */
    static String resolveNotifyText(LoginClientInfo loginInfo, int strike) {
        LoginScopeEnum scopeEnum = LoginScopeEnum.fromType(LoginScopeEnum.normalizeScope(loginInfo.getScope()));
        String variant = resolveVariant(strike, loginInfo.getBusinessIdleCloseStrike());
        if (variant == null) {
            return null;
        }
        IdleNotifyTextCache cache = IdleNotifyTextCache.getInstance();
        String configured = pickConfigured(cache.getTenant(loginInfo.getAppKey()), scopeEnum, variant);
        if (configured == null) {
            configured = pickConfigured(cache.getGlobal(), scopeEnum, variant);
        }
        String text = StringUtils.isNotBlank(configured) ? configured : builtinText(scopeEnum, variant);
        if (text == null) {
            return null;
        }
        if (variant.endsWith(IdleNotifyConstant.VARIANT_PRE_CLOSE)) {
            return formatIdleSeconds(text, Math.max(NumberConstant.NUMBER_1, loginInfo.getBusinessIdleSeconds()));
        }
        return text;
    }

    /**
     * 首次为 {@link IdleNotifyConstant#VARIANT_FIRST}；其后为 {@code {strike}:pre-close|repeat}。
     */
    static String resolveVariant(int strike, int closeAt) {
        if (strike <= NumberConstant.NUMBER_0) {
            return null;
        }
        if (strike == IdleNotifyConstant.STRIKE_FIRST) {
            return IdleNotifyConstant.VARIANT_FIRST;
        }
        boolean closeNowOrLater = closeAt > NumberConstant.NUMBER_0 && closeAt >= strike;
        String suffix = closeNowOrLater
                ? IdleNotifyConstant.VARIANT_PRE_CLOSE
                : IdleNotifyConstant.VARIANT_REPEAT;
        return IdleNotifyConstant.variantWithSuffix(strike, suffix);
    }

    static String pickConfigured(Map<String, String> fields, LoginScopeEnum scopeEnum, String variant) {
        if (fields == null || fields.isEmpty() || variant == null) {
            return null;
        }
        String byName = fields.get(IdleNotifyConstant.fieldKey(scopeEnum.getName(), variant));
        if (StringUtils.isNotBlank(byName)) {
            return byName;
        }
        String byType = fields.get(IdleNotifyConstant.fieldKey(String.valueOf(scopeEnum.getType()), variant));
        if (StringUtils.isNotBlank(byType)) {
            return byType;
        }
        String wildcard = fields.get(IdleNotifyConstant.fieldKey(IdleNotifyConstant.WILDCARD_SCOPE, variant));
        return StringUtils.isNotBlank(wildcard) ? wildcard : null;
    }

    static String builtinText(LoginScopeEnum scopeEnum, String variant) {
        if (IdleNotifyConstant.VARIANT_FIRST.equals(variant)) {
            return switch (scopeEnum) {
                case CS_AGENT -> MessageConstant.BUSINESS_IDLE_PROMPT_CS_AGENT;
                case CS_VISITOR -> MessageConstant.BUSINESS_IDLE_PROMPT_CS_VISITOR;
                case NORMAL -> MessageConstant.BUSINESS_IDLE_PROMPT_NORMAL;
            };
        }
        if (variant.endsWith(IdleNotifyConstant.VARIANT_PRE_CLOSE)
                && variant.startsWith(IdleNotifyConstant.BUILTIN_STRIKE_PREFIX)) {
            return MessageConstant.BUSINESS_IDLE_PRE_CLOSE;
        }
        if (variant.endsWith(IdleNotifyConstant.VARIANT_REPEAT)
                && variant.startsWith(IdleNotifyConstant.BUILTIN_STRIKE_PREFIX)) {
            return MessageConstant.BUSINESS_IDLE_REPEAT_PROMPT;
        }
        return null;
    }

    private static String formatIdleSeconds(String template, int idleSec) {
        if (template.contains(IdleNotifyConstant.PLACEHOLDER_INT)
                || template.contains(IdleNotifyConstant.PLACEHOLDER_STRING)) {
            try {
                return String.format(template, idleSec);
            } catch (Exception e) {
                log.warn("业务空闲预断开文案格式化失败，使用原文: {}", e.getMessage());
            }
        }
        return template;
    }

    private static Packet buildNotifyPacket(LoginClientInfo loginInfo, String text) {
        long now = TimeUtil.currentTimeMillis();
        Metadata metadata = new Metadata();
        metadata.ensureIngress().setAppKey(loginInfo.getAppKey());
        metadata.ensureIngress().setServerTime(now);
        Message message = new Message(
                MessageContext.idGenerator().generateIdStr(),
                null,
                loginInfo.getIdentity(),
                MessageContentTypeEnum.TEXT_CONTENT.getType(),
                Serializer.JSON.serializeToString(new ServerNotifyContent(text)),
                now,
                metadata);
        return new Packet(
                loginInfo.getProtocol(),
                loginInfo.getProtocolVersion(),
                MessageContext.idGenerator().generateId(),
                loginInfo.getDeviceType(),
                NetworkEnum.OTHER.getValue(),
                Encrypt.SymmetryEncrypt.NONE.getValue(),
                Serializer.JSON.getValue(),
                MessageTypeEnum.SERVER_NOTIFY.getType(),
                message);
    }


    /**
     * 业务上行后 strike 被置 0（或尚未计数）。此时空闲提示和关连都应取消。
     */
    public static boolean isIdleStrikeCleared(ChannelHandlerContext ctx) {
        if (ctx == null) {
            return true;
        }
        Integer current = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_BUSINESS_IDLE_STRIKE);
        return current == null || current == IdleNotifyConstant.STRIKE_CLEARED;
    }

    /**
     * 关连是否仍然成立：连接还在，且当前 strike 仍达到关连档。
     * 通知写出回调前对方发了业务包会把 strike 清零，此时必须取消关连。
     */
    public static boolean isCloseStillDue(ChannelHandlerContext ctx) {
        if (ctx == null || ctx.channel() == null || !ctx.channel().isActive()) {
            return false;
        }
        LoginClientInfo info = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        if (info == null) {
            return false;
        }
        int closeAt = resolveCloseAtStrike(info);
        if (closeAt <= NumberConstant.NUMBER_0) {
            return false;
        }
        Integer current = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_BUSINESS_IDLE_STRIKE);
        return current != null && current >= closeAt;
    }

    /**
     * @return -1 表示不因次数关连（{@code businessIdleCloseStrike <= 0}）；否则为第几次关连
     */
    private static int resolveCloseAtStrike(LoginClientInfo loginInfo) {
        int t = loginInfo.getBusinessIdleCloseStrike();
        if (t <= NumberConstant.NUMBER_0) {
            return IdleNotifyConstant.CLOSE_STRIKE_DISABLED;
        }
        return t;
    }
}
