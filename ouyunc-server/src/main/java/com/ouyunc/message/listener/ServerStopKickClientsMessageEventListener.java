package com.ouyunc.message.listener;

import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.core.listener.EventListener;
import com.ouyunc.core.listener.MessageEventListener;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 服务停止：通知客户端主动重连 → 宽限期 → 强制关闭残留连接。
 */
@EventListener(order = 10)
class ServerStopKickClientsMessageEventListener implements MessageEventListener<MessageEvent> {
    private static final Logger log = LoggerFactory.getLogger(ServerStopKickClientsMessageEventListener.class);

    @Override
    public EventType type() {
        return MessageEventTypeEnum.SERVER_STOP;
    }

    @Override
    public void onEvent(MessageEvent event) {
        MessageServerContext.enterDrainMode();
        if (!MessageServerContext.serverProperties().isShutdownKickClients()) {
            log.warn("shutdown.kick-clients=false，跳过停机踢线/通知");
            return;
        }
        int notified = ClientHelper.notifyAllLocalClientsToReconnect();
        awaitClientReconnectGrace(notified, MessageServerContext.serverProperties().getDrainWaitSeconds());
        int forced = ClientHelper.forceCloseAllLocalClients();
        log.warn("服务停止踢线完成, notified={}, forceClosed={}", notified, forced);
    }

    private static void awaitClientReconnectGrace(int notified, int graceSeconds) {
        if (graceSeconds <= 0) {
            return;
        }
        log.warn("已通知客户端主动重连 notified={}，等待 {} 秒后强制关闭残留连接", notified, graceSeconds);
        try {
            TimeUnit.SECONDS.sleep(graceSeconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待客户端主动断开被中断，继续强制关闭残留连接");
        }
    }
}
