package com.ouyunc.message.listener;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.EventRingEnum;
import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.core.listener.EventListener;
import com.ouyunc.core.listener.MessageEventListener;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.message.helper.CsAgentPresenceNotifyHelper;

/**
 * 坐席 IM 通道关闭 → CS CHANNEL_CLOSE。与 {@link CsAgentPresenceLoginMessageEventListener} 对称；
 * 覆盖心跳超时 / 业务空闲关连 / 杀进程等经 {@code CLIENT_LOGOUT} 的路径，与遗嘱无关。
 */
@EventListener(order = 50, ring = EventRingEnum.CLIENT_LOGOUT)
class CsAgentPresenceLogoutMessageEventListener implements MessageEventListener<MessageEvent> {

    @Override
    public EventType type() {
        return MessageEventTypeEnum.CLIENT_LOGOUT;
    }

    @Override
    public void onEvent(MessageEvent event) {
        Object source = event.getSource();
        if (!(source instanceof LoginClientInfo loginClientInfo)) {
            return;
        }
        CsAgentPresenceNotifyHelper.notifyIfCsAgent(
                loginClientInfo,
                MessageConstant.CS_AGENT_PRESENCE_CHANNEL_CLOSE,
                MessageConstant.CS_AGENT_PRESENCE_CHANNEL_CLOSE);
    }
}
