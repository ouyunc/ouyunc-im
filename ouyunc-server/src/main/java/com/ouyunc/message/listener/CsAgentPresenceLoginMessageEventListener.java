package com.ouyunc.message.listener;

import com.ouyunc.base.constant.enums.EventRingEnum;
import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.model.CsAgentPresenceNotifyPayload;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.core.listener.EventListener;
import com.ouyunc.core.listener.MessageEventListener;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ClientLoginEventPayload;
import com.ouyunc.message.helper.CsAgentPresenceNotifyHelper;

/**
 * 坐席 IM 登录成功 → CS CHANNEL_OPEN。与 {@link CsAgentPresenceLogoutMessageEventListener} 对称；
 * 独立于好友上线通知，不受 {@code enableAlive} 影响。
 */
@EventListener(order = 50, ring = EventRingEnum.CLIENT_LOGIN)
class CsAgentPresenceLoginMessageEventListener implements MessageEventListener<MessageEvent> {

    @Override
    public EventType type() {
        return MessageEventTypeEnum.CLIENT_LOGIN;
    }

    @Override
    public void onEvent(MessageEvent event) {
        Object source = event.getSource();
        if (!(source instanceof ClientLoginEventPayload payload)) {
            return;
        }
        Object login = payload.loginInfo();
        if (!(login instanceof LoginClientInfo loginClientInfo)) {
            return;
        }
        CsAgentPresenceNotifyHelper.notifyIfCsAgent(
                loginClientInfo,
                CsAgentPresenceNotifyPayload.EVENT_CHANNEL_OPEN,
                CsAgentPresenceNotifyPayload.REASON_CHANNEL_OPEN);
    }
}
