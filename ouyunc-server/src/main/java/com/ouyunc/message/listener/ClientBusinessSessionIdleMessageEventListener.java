package com.ouyunc.message.listener;

import com.ouyunc.base.constant.IdleNotifyConstant;
import com.ouyunc.base.constant.enums.EventRingEnum;
import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.core.listener.EventListener;
import com.ouyunc.core.listener.MessageEventListener;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ClientBusinessSessionIdlePayload;
import com.ouyunc.message.helper.BusinessIdleNotifyHelper;
import com.ouyunc.message.handler.BusinessIdleStateHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端业务会话空闲：按 {@link ClientBusinessSessionIdlePayload#strike()} 向本连接下行 IM 提示；关连由
 * {@link BusinessIdleStateHandler} 在 {@link com.ouyunc.base.packet.message.content.LoginContent#getBusinessIdleCloseStrike()}
 * {@code >=} {@link IdleNotifyConstant#STRIKE_FIRST} 且达到次数时关连；{@code <=} {@link IdleNotifyConstant#STRIKE_CLEARED} 不关。
 * <p>不通知 CS；通道关闭后由 {@link CsAgentPresenceLogoutMessageEventListener} 投递 MQ。ticket SLA 仍由 CS Scanner 负责。</p>
 */
@EventListener(ring = EventRingEnum.CLIENT_BUSINESS_SESSION_IDLE)
class ClientBusinessSessionIdleMessageEventListener implements MessageEventListener<MessageEvent> {

    private static final Logger log = LoggerFactory.getLogger(ClientBusinessSessionIdleMessageEventListener.class);

    @Override
    public EventType type() {
        return MessageEventTypeEnum.CLIENT_BUSINESS_SESSION_IDLE;
    }

    @Override
    public void onEvent(MessageEvent event) {
        if (!(event.getSource() instanceof ClientBusinessSessionIdlePayload payload)) {
            log.warn("CLIENT_BUSINESS_SESSION_IDLE 事件 source 类型非 ClientBusinessSessionIdlePayload, eventId={}", event.getId());
            return;
        }
        LoginClientInfo loginInfo = payload.loginInfo();
        ChannelHandlerContext ctx = payload.ctx();
        int strike = payload.strike();
        if (loginInfo == null || ctx == null) {
            log.warn("业务空闲事件缺少 loginInfo 或 ctx, eventId={}, strike={}", event.getId(), strike);
            return;
        }
        String chId = ctx.channel().id().asShortText();
        if (log.isDebugEnabled()) {
            log.debug("业务会话空闲: appKey={}, identity={}, strike={}, channel={}",
                    loginInfo.getAppKey(), loginInfo.getIdentity(), strike, chId);
        }
        BusinessIdleNotifyHelper.notifyIdle(ctx, loginInfo, strike);
    }
}
