package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.LoginScopeEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.NetworkEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.ServerNotifyContent;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 业务读空闲：向当前连接下行 {@link MessageTypeEnum#SERVER_NOTIFY} 提示（仅 IM，不通知 CS）。
 */
public final class BusinessIdleNotifyHelper {

    private static final Logger log = LoggerFactory.getLogger(BusinessIdleNotifyHelper.class);

    private BusinessIdleNotifyHelper() {
    }

    /**
     * 按 strike 向触发空闲的本连接发送提示。
     *
     * @param strike 连续业务空闲次数（1=首次提示，2=即将断开预警）
     */
    public static void notifyIdle(ChannelHandlerContext ctx, LoginClientInfo loginInfo, int strike) {
        if (ctx == null || loginInfo == null || strike <= 0) {
            return;
        }
        String text = resolveNotifyText(loginInfo, strike);
        if (text == null) {
            return;
        }
        ctx.channel().eventLoop().execute(() -> {
            if (!ctx.channel().isActive()) {
                return;
            }
            try {
                Packet packet = buildNotifyPacket(loginInfo, text);
                MessageHelper.syncSendMessageWithoutInterceptor(packet, MessageHelper.buildTarget(loginInfo));
            } catch (Exception e) {
                log.warn("业务空闲提示下发失败, appKey={}, identity={}, strike={}: {}",
                        loginInfo.getAppKey(), loginInfo.getIdentity(), strike, e.getMessage());
            }
        });
    }

    static String resolveNotifyText(LoginClientInfo loginInfo, int strike) {
        int scope = LoginScopeEnum.normalizeScope(loginInfo.getScope());
        return switch (strike) {
            case 1 -> scope == LoginScopeEnum.CS_AGENT.getType()
                    ? MessageConstant.BUSINESS_IDLE_PROMPT_CS_AGENT
                    : MessageConstant.BUSINESS_IDLE_PROMPT_CS_VISITOR;
            case 2 -> {
                int closeAt = loginInfo.getBusinessIdleCloseStrike();
                int idleSec = Math.max(1, loginInfo.getBusinessIdleSeconds());
                if (closeAt > strike) {
                    yield String.format(MessageConstant.BUSINESS_IDLE_PRE_CLOSE, idleSec);
                }
                yield MessageConstant.BUSINESS_IDLE_REPEAT_PROMPT;
            }
            default -> null;
        };
    }

    private static Packet buildNotifyPacket(LoginClientInfo loginInfo, String text) {
        long now = TimeUtil.currentTimeMillis();
        Metadata metadata = new Metadata();
        metadata.setAppKey(loginInfo.getAppKey());
        metadata.setServerTime(now);
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
