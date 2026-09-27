package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.LoginScopeEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.NetworkEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.SendCallback;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.ServerNotifyContent;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.cache.IdleNotifyTextCache;
import com.ouyunc.message.handler.BusinessIdleStateHandler;
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

    /** Redis field 通配 scope。 */
    static final String WILDCARD_SCOPE = "*";
    /** 第 2 档且仍将关连。 */
    static final String VARIANT_PRE_CLOSE = "pre-close";
    /** 第 2 档及以后不关连。 */
    static final String VARIANT_REPEAT = "repeat";

    private BusinessIdleNotifyHelper() {
    }

    /**
     * 按 strike 向触发空闲的本连接发送提示。
     *
     * @param strike 连续业务空闲次数（1=首次提示，2+=预断开/重复提示，文案按 Redis field 可扩展）
     */
    public static void notifyIdle(ChannelHandlerContext ctx, LoginClientInfo loginInfo, int strike) {
        if (ctx == null || loginInfo == null || strike <= 0) {
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
            if (BusinessIdleStateHandler.isIdleStrikeCleared(ctx)) {
                return;
            }
            try {
                Packet packet = buildNotifyPacket(loginInfo, text);
                SendCallback afterSend = closeAfter
                        ? unused -> closeIfStillDue(ctx)
                        : unused -> { };
                MessageHelper.syncSendMessageWithoutInterceptor(
                        packet, MessageHelper.buildTarget(loginInfo), afterSend);
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
        return closeAt > 0 && strike >= closeAt;
    }

    private static void closeIfStillDue(ChannelHandlerContext ctx) {
        if (ctx == null || ctx.channel() == null) {
            return;
        }
        Runnable close = () -> {
            if (BusinessIdleStateHandler.isCloseStillDue(ctx)) {
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
        if (variant.endsWith(VARIANT_PRE_CLOSE)) {
            return formatIdleSeconds(text, Math.max(1, loginInfo.getBusinessIdleSeconds()));
        }
        return text;
    }

    /**
     * {@code 1} / {@code 2:pre-close} / {@code 2:repeat}；strike&gt;2 同样按是否关连拼 variant。
     */
    static String resolveVariant(int strike, int closeAt) {
        if (strike <= 0) {
            return null;
        }
        if (strike == 1) {
            return "1";
        }
        // 即将关或本档就关：用 pre-close；永不关连：repeat
        boolean closeNowOrLater = closeAt > 0 && closeAt >= strike;
        String suffix = closeNowOrLater ? VARIANT_PRE_CLOSE : VARIANT_REPEAT;
        return strike + ":" + suffix;
    }

    static String pickConfigured(Map<String, String> fields, LoginScopeEnum scopeEnum, String variant) {
        if (fields == null || fields.isEmpty() || variant == null) {
            return null;
        }
        String byName = fields.get(scopeEnum.getName() + ":" + variant);
        if (StringUtils.isNotBlank(byName)) {
            return byName;
        }
        String byType = fields.get(scopeEnum.getType() + ":" + variant);
        if (StringUtils.isNotBlank(byType)) {
            return byType;
        }
        String wildcard = fields.get(WILDCARD_SCOPE + ":" + variant);
        return StringUtils.isNotBlank(wildcard) ? wildcard : null;
    }

    static String builtinText(LoginScopeEnum scopeEnum, String variant) {
        if ("1".equals(variant)) {
            return switch (scopeEnum) {
                case CS_AGENT -> MessageConstant.BUSINESS_IDLE_PROMPT_CS_AGENT;
                case CS_VISITOR -> MessageConstant.BUSINESS_IDLE_PROMPT_CS_VISITOR;
                case NORMAL -> MessageConstant.BUSINESS_IDLE_PROMPT_NORMAL;
            };
        }
        if (variant.endsWith(VARIANT_PRE_CLOSE) && variant.startsWith("2:")) {
            return MessageConstant.BUSINESS_IDLE_PRE_CLOSE;
        }
        if (variant.endsWith(VARIANT_REPEAT) && variant.startsWith("2:")) {
            return MessageConstant.BUSINESS_IDLE_REPEAT_PROMPT;
        }
        return null;
    }

    private static String formatIdleSeconds(String template, int idleSec) {
        if (template.contains("%d") || template.contains("%s")) {
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
        metadata.getIngress().setAppKey(loginInfo.getAppKey());
        metadata.getIngress().setServerTime(now);
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
}
