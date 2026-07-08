package com.ouyunc.message.processor.http.push.delivery;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.model.ClientInfo;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * HTTP 推送投递公共能力。
 */
public final class HttpPushDeliverySupport {

    private static final Logger log = LoggerFactory.getLogger(HttpPushDeliverySupport.class);

    private HttpPushDeliverySupport() {
    }

    public static void publishException(ExceptionCodeEnum code, String message, Packet packet) {
        MessageServerContext.publishEvent(new MessageEvent(
                ExceptionEventPayload.of(code, message, packet),
                MessageEventTypeEnum.EXCEPTION), true);
    }

    public static void pushSessionOnline(Packet packet, boolean forceSelfSync, String offlineLogLabel) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        ClientInfo clientInfo = MessageServerContext.localClientInfo(appKey, message.getFrom());
        if (forceSelfSync || (clientInfo != null && clientInfo.getSelfSync())) {
            List<LoginClientInfo> fromClients = ClientHelper.onlineAll(appKey, message.getFrom(),
                    MessageServerContext.deviceType(appKey, packet.getDeviceType()));
            if (CollectionUtils.isNotEmpty(fromClients)) {
                MessageHelper.asyncSendMessage(packet, fromClients);
            }
        }
        List<LoginClientInfo> toClients = ClientHelper.onlineAll(appKey, message.getTo());
        if (CollectionUtils.isEmpty(toClients)) {
            log.debug("HTTP 推送{}接收方 {} 不在线，已写入会话索引", offlineLogLabel, message.getTo());
        } else {
            MessageHelper.asyncSendMessage(packet, toClients);
        }
    }
}
