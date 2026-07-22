package com.ouyunc.message.helper;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.model.CsTicketActivityNotifyPayload;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.AppKeyUtil;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.properties.MessageServerProperties;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.cs.CsImSessionRoute;
import com.ouyunc.repository.cs.CsMessageScopeHelper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** IM 客服消息持久化后通过 MQ 通知 CS，驱动 ticket 活动 / 托管恢复 / SLA 调度。 */
public final class CsTicketActivityNotifyHelper {

    private static final Logger log = LoggerFactory.getLogger(CsTicketActivityNotifyHelper.class);

    private CsTicketActivityNotifyHelper() {
    }

    public static void notifyAfterSave(Packet packet, CsImSessionRoute route) {
        if (packet == null || route == null) {
            return;
        }
        MessageServerProperties props = serverProperties();
        if (props == null || !props.isCsTicketActivityEnabled()) {
            return;
        }
        Long ticketId = parseTicketId(route);
        if (ticketId == null) {
            return;
        }
        Message message = packet.getMessage();
        if (message == null) {
            return;
        }
        String appKey = AppKeyUtil.defaultIfBlank(
                message.getMetadata() != null ? message.getMetadata().getAppKey() : null);
        Long serverTime = message.getMetadata() != null ? message.getMetadata().getServerTime() : null;
        CsTicketActivityNotifyPayload body = new CsTicketActivityNotifyPayload(
                appKey, ticketId, packet.getPacketId(), message.getFromType(),
                serverTime != null ? serverTime : System.currentTimeMillis());
        String topic = MqConstant.MQ_CS_TICKET_ACTIVITY_TOPIC;
        String key = CsTicketActivityNotifyPayload.messageKey(appKey, ticketId);
        String json = JSON.toJSONString(body);
        DefaultRepository.INSTANCE.publishJsonAsync(topic, key, json, "CS ticket-activity MQ, ticketId=" + ticketId);
        if (log.isDebugEnabled()) {
            log.debug("CS ticket-activity 已投递 MQ, topic={}, ticketId={}, packetId={}",
                    topic, ticketId, packet.getPacketId());
        }
    }

    private static MessageServerProperties serverProperties() {
        if (MessageServerContext.messageProperties instanceof MessageServerProperties props) {
            return props;
        }
        return null;
    }

    private static Long parseTicketId(CsImSessionRoute route) {
        String scopeId = CsMessageScopeHelper.ticketMessageScopeId(route);
        if (StringUtils.isBlank(scopeId)) {
            return null;
        }
        try {
            return Long.parseLong(scopeId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
