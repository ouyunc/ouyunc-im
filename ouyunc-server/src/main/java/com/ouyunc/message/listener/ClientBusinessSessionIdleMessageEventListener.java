package com.ouyunc.message.listener;

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
 * {@link BusinessIdleStateHandler} 在 {@link com.ouyunc.base.packet.message.content.LoginContent#getBusinessIdleCloseStrike()} {@code >=1} 且达到次数时关连；{@code <=0} 不关。
 * <p>不通知 CS；ticket SLA 仍由 CS Scanner 负责。</p>
 */
@EventListener(order = 100, ring = EventRingEnum.CLIENT_BUSINESS_SESSION_IDLE)
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
        if (log.isInfoEnabled()) {
            log.debug("业务会话空闲: appKey={}, identity={}, strike={}, channel={}", loginInfo.getAppKey(), loginInfo.getIdentity(), strike, chId);
        }
        switch (strike) {
            case 1 -> onPrompt(payload);
            case 2 -> onEscrow(payload);
            default -> {
                // 第 3 次及以后无单独钩子，业务可在 onEvent 后统一处理或扩展
            }
        }
    }

    /** 第 1 次连续业务空闲：IM 下行提示。 */
    protected void onPrompt(ClientBusinessSessionIdlePayload payload) {
        BusinessIdleNotifyHelper.notifyIdle(payload.ctx(), payload.loginInfo(), payload.strike());
    }

    /** 第 2 次连续业务空闲：即将断开预警（若配置了关连档位数）。 */
    protected void onEscrow(ClientBusinessSessionIdlePayload payload) {
        BusinessIdleNotifyHelper.notifyIdle(payload.ctx(), payload.loginInfo(), payload.strike());
    }
}
