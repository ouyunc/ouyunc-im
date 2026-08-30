package com.ouyunc.message.helper;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.IngressSourceEnum;
import com.ouyunc.base.constant.enums.LoginScopeEnum;
import com.ouyunc.base.constant.enums.MessageDeliveryChannelEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageFromToTypeEnum;
import com.ouyunc.base.constant.enums.YesOrNo;
import com.ouyunc.base.model.ClientInfo;
import com.ouyunc.base.model.CsAgentPresenceNotifyPayload;
import com.ouyunc.base.model.CsTicketActivityNotifyPayload;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.AppKeyUtil;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.QosClaimIdentities;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.properties.MessageServerProperties;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.cs.CsDeliveryChannelHelper;
import com.ouyunc.repository.cs.CsImSessionRoute;
import com.ouyunc.repository.cs.CsMessageScopeHelper;
import com.ouyunc.message.processor.http.push.HttpPushSupportedTypes;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 客服 IM 助手（server 侧统一入口）：scope/渠道、会话校验、投递、落库后副作用、坐席 presence。
 *
 * <p>路由主键 = {@code ticketId}（消息 {@code correlationId}）；通道语义仍为访客↔入口。</p>
 * <p>server 对外只通过本类访问客服能力；repository 层内部工具不直接对 server 暴露。</p>
 */
public final class CsHelper {

    private static final Logger log = LoggerFactory.getLogger(CsHelper.class);

    private CsHelper() {
    }

    // -------------------------------------------------------------------------
    // scope / 渠道（委托 repository 工具，server 对外只走本类）
    // -------------------------------------------------------------------------

    /** ticket 消息索引 scopeId（= ticketId）。 */
    public static String ticketMessageScopeId(CsImSessionRoute route) {
        return CsMessageScopeHelper.ticketMessageScopeId(route);
    }

    /** 已读 offset / 未读归属的真实用户 id（坐席用 assigneeId，访客用 userId）。 */
    public static String resolveReaderId(Message message, CsImSessionRoute route) {
        return CsMessageScopeHelper.resolveReaderId(message, route);
    }

    /**
     * 解析接收方下行渠道：坐席始终 IM；访客按 route.channelType（whatsapp/telegram/line → 外渠 Kafka）。
     */
    public static MessageDeliveryChannelEnum resolveRecipientChannel(CsImSessionRoute route, String recipientId) {
        return CsDeliveryChannelHelper.resolveRecipientChannel(route, recipientId);
    }

    /**
     * CS ticket.channelType → IM 投递枚举。im 走长连接；whatsapp/telegram/line 走 Kafka 出站。
     */
    public static MessageDeliveryChannelEnum fromChannelType(String channelType) {
        return CsDeliveryChannelHelper.fromChannelType(channelType);
    }

    public static boolean isExternalVisitorRoute(CsImSessionRoute route) {
        return CsDeliveryChannelHelper.isExternalVisitorRoute(route);
    }

    // -------------------------------------------------------------------------
    // 校验 / prepare
    // -------------------------------------------------------------------------

    public record PrepareOutcome(boolean accepted, CsImSessionRoute route, String rejectReason) {
        public static PrepareOutcome ok(CsImSessionRoute route) {
            return new PrepareOutcome(true, route, null);
        }

        public static PrepareOutcome reject(String reason) {
            return new PrepareOutcome(false, null, reason);
        }
    }

    public static PrepareOutcome prepare(Packet packet) {
        Message message = packet.getMessage();
        if (message == null || message.getMetadata() == null) {
            return PrepareOutcome.reject("消息或元数据为空");
        }
        String appKey = message.getMetadata().getAppKey();
        String from = message.getFrom();
        String to = message.getTo();
        if (StringUtils.isAnyBlank(appKey, from, to)) {
            return PrepareOutcome.reject("appKey/from/to 为空");
        }
        if (StringUtils.equals(from, to)) {
            return PrepareOutcome.reject("from/to 不能相同");
        }

        int fromType = message.getFromType();
        if (fromType != MessageFromToTypeEnum.CS_AGENT.getType()
                && fromType != MessageFromToTypeEnum.CS_VISITOR.getType()) {
            return PrepareOutcome.reject("非客服访客/坐席身份，不能发送客服消息");
        }

        String ticketId = message.getCorrelationId();
        if (StringUtils.isBlank(ticketId)) {
            return PrepareOutcome.reject("客服消息必须携带 ticketId(correlationId)");
        }
        if (!HttpPushSupportedTypes.isSupportedCsContent(packet)) {
            return PrepareOutcome.reject("客服不支持该消息类型 contentType=" + message.getContentType());
        }

        CsImSessionRoute route = DefaultRepository.INSTANCE.getCsImSessionRoute(appKey, ticketId);
        if (route == null) {
            return PrepareOutcome.reject("客服会话路由不存在，请先创单并写入路由");
        }
        if (StringUtils.isAnyBlank(route.ticketId(), route.userId(), route.serviceIdentity(), route.sessionId())) {
            return PrepareOutcome.reject("会话路由数据不完整");
        }
        if (!route.hasRequiredDeliveryFields()) {
            return PrepareOutcome.reject("尚未分配坐席或机器人，不能发消息");
        }
        if (!route.isActive(YesOrNo.YES.getCode())) {
            return PrepareOutcome.reject("咨询单已关闭或不可收发消息");
        }

        if (fromType == MessageFromToTypeEnum.CS_AGENT.getType()) {
            if (!StringUtils.equals(from, route.assigneeId())) {
                return PrepareOutcome.reject("当前坐席无权在该会话发消息");
            }
            if (!StringUtils.equals(to, route.userId())) {
                return PrepareOutcome.reject("坐席 to 必须为访客 userId");
            }
            // from 改写推迟到 refreshDelivery 之后，以便转接窗口用真实 assignee 校验发送方
        } else {
            if (!StringUtils.equals(from, route.userId()) || !StringUtils.equals(to, route.serviceIdentity())) {
                return PrepareOutcome.reject("访客 from/to 与咨询单不一致（须访客→入口）");
            }
            String channelSessionId = IdentityUtil.sessionId(from, to);
            if (!StringUtils.equals(route.sessionId(), channelSessionId)) {
                return PrepareOutcome.reject("路由 sessionId 与访客/入口不一致");
            }
        }

        return PrepareOutcome.ok(route);
    }

    /**
     * 投递前二次读 assignee/epoch。转接发生在 prepare 之后时改打新座席；关单则拒绝。
     * 坐席侧若已不是当前 assignee，拒绝发送，避免旧座席在转接窗口继续发言。
     */
    public static PrepareOutcome refreshDelivery(Packet packet, CsImSessionRoute snapshot) {
        if (packet == null || packet.getMessage() == null || snapshot == null) {
            return PrepareOutcome.reject("投递刷新缺少路由快照");
        }
        String appKey = packet.getMessage().getMetadata() != null
                ? packet.getMessage().getMetadata().getAppKey()
                : null;
        CsImSessionRoute live = DefaultRepository.INSTANCE.mergeCsImSessionRouteDelivery(appKey, snapshot);
        if (live == null || !live.hasRequiredDeliveryFields()) {
            return PrepareOutcome.reject("咨询单尚未分配坐席或机器人，或路由已关闭");
        }
        if (!live.isActive(YesOrNo.YES.getCode())) {
            return PrepareOutcome.reject("咨询单已关闭或不可收发消息");
        }
        Message message = packet.getMessage();
        if (message.getFromType() == MessageFromToTypeEnum.CS_AGENT.getType()) {
            if (!StringUtils.equals(message.getFrom(), live.assigneeId())) {
                return PrepareOutcome.reject("当前坐席无权在该会话发消息（可能已转接）");
            }
        }
        if (log.isDebugEnabled() && !live.epoch().equals(snapshot.epoch())) {
            log.debug(
                    "客服路由 epoch 已前进, ticketId={}, {} -> {}, assignee {} -> {}",
                    live.ticketId(),
                    snapshot.epoch(),
                    live.epoch(),
                    snapshot.assigneeId(),
                    live.assigneeId());
        }
        return PrepareOutcome.ok(live);
    }

    /**
     * 坐席发消息对外 from 改为入口 identity；须在 {@link #refreshDelivery} 通过之后调用。
     * 改写前会把真实发送方记入 metadata.qosClaimIdentity，供 QoS 幂等键使用。
     */
    public static void rewriteAgentFrom(Packet packet, CsImSessionRoute route) {
        if (packet == null || packet.getMessage() == null || route == null) {
            return;
        }
        if (packet.getMessage().getFromType() != MessageFromToTypeEnum.CS_AGENT.getType()) {
            return;
        }
        if (StringUtils.isNotBlank(route.serviceIdentity())) {
            // HTTP 推送无 AuthValidator：改写前记下真实发送方，供 QoS claim 对齐登录身份
            QosClaimIdentities.rememberIfAbsent(packet.getMessage(), packet.getMessage().getFrom());
            packet.getMessage().setFrom(route.serviceIdentity());
        }
    }

    public static void publishReject(Packet packet, String reason) {
        MessageServerContext.publishEvent(new MessageEvent(
                ExceptionEventPayload.of(ExceptionCodeEnum.CS_SESSION_ROUTE_ERROR, reason, packet),
                MessageEventTypeEnum.EXCEPTION), true);
    }

    // -------------------------------------------------------------------------
    // 投递
    // -------------------------------------------------------------------------


    public static void deliverMessage(Packet packet, CsImSessionRoute route) {
        deliverMessage(packet, route, null);
    }

    /**
     * 客服下行投递（聊天 / 已读 / 撤回后推送共用）。
     *
     * @param forceSelfSync {@code null}：不向发送方多端同步（已读回执）；
     *                      非 {@code null}：先按该值做发送方 selfSync，再投递给接收方
     */
    public static void deliverMessage(Packet packet, CsImSessionRoute route, Boolean forceSelfSync) {
        if (route == null) {
            log.error("客服投递缺少会话路由, packetId={}", packet.getPacketId());
            return;
        }
        if (forceSelfSync != null) {
            syncCsSenderDevices(packet, route, forceSelfSync);
        }
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        String recipientId = resolveImRecipientId(message.getTo(), route);
        if (StringUtils.isBlank(recipientId)) {
            log.debug("客服投递暂无目标 to={}, 已落库, packetId={}", message.getTo(), packet.getPacketId());
            return;
        }
        // 机器人/虚拟客服不挂长连：跳过对 assignee 的 IM 推送（入站靠 ticket-activity MQ）
        if (StringUtils.equals(recipientId, route.assigneeId())
                && !route.assigneeRequiresImLongConnection()) {
            log.debug(
                    "坐席非长连类型跳过 IM 推送, ticketId={}, assigneeId={}, agentType={}, packetId={}",
                    route.ticketId(),
                    route.assigneeId(),
                    route.agentType(),
                    packet.getPacketId());
            return;
        }
        MessageDeliveryChannelEnum channel = resolveRecipientChannel(route, recipientId);
        if (channel.isIm()) {
            pushImUserIfOnline(packet, appKey, recipientId);
            return;
        }
        log.debug("客服外渠下行, ticketId={}, to={}, channel={}, packetId={}",
                route.ticketId(), recipientId, channel.getKey(), packet.getPacketId());
        DefaultRepository.INSTANCE.publishExternalChannelOutbound(packet, recipientId, channel);
    }

    public static String resolveImRecipientId(String recipientId, CsImSessionRoute route) {
        if (StringUtils.isBlank(recipientId) || route == null) {
            return null;
        }
        if (StringUtils.equals(recipientId, route.serviceIdentity())) {
            return route.assigneeId();
        }
        return recipientId;
    }

    private static void pushImUserIfOnline(Packet packet, String appKey, String userId) {
        List<LoginClientInfo> clients = ClientHelper.onlineAll(appKey, userId);
        if (CollectionUtils.isEmpty(clients)) {
            log.debug("IM 用户 {} 不在线，已写入会话索引", userId);
            return;
        }
        MessageHelper.asyncSendMessage(packet, clients);
    }

    private static void syncCsSenderDevices(Packet packet, CsImSessionRoute route, boolean forceSelfSync) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        String syncIdentity = resolveSenderSyncIdentity(message.getFrom(), route);
        if (StringUtils.isBlank(syncIdentity)) {
            return;
        }
        boolean httpPush = message.getMetadata() != null
                && IngressSourceEnum.isHttpPush(message.getMetadata().getIngressSource());
        if (!forceSelfSync) {
            ClientInfo clientInfo = MessageServerContext.localClientInfo(appKey, syncIdentity);
            if (httpPush) {
                if (clientInfo != null && !Boolean.TRUE.equals(clientInfo.getSelfSync())) {
                    return;
                }
            } else if (clientInfo == null || !clientInfo.getSelfSync()) {
                return;
            }
        }
        List<LoginClientInfo> senderDevices = httpPush
                ? ClientHelper.onlineAll(appKey, syncIdentity)
                : ClientHelper.onlineAll(appKey, syncIdentity,
                MessageServerContext.deviceType(appKey, packet.getDeviceType()));
        if (CollectionUtils.isNotEmpty(senderDevices)) {
            MessageHelper.asyncSendMessage(packet, senderDevices);
        }
    }

    private static String resolveSenderSyncIdentity(String from, CsImSessionRoute route) {
        if (route == null) {
            return from;
        }
        if (StringUtils.equals(from, route.serviceIdentity())) {
            return route.assigneeId();
        }
        return from;
    }

    // -------------------------------------------------------------------------
    // 落库后副作用：ticket lm + activity MQ
    // -------------------------------------------------------------------------

    public static void saveChatLastMessage(DefaultRepository repository, CsImSessionRoute route, Packet packet) {
        if (repository == null || route == null || packet == null) {
            return;
        }
        String ticketId = route.ticketId();
        if (StringUtils.isBlank(ticketId)) {
            return;
        }
        long expireMs = MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP;
        repository.saveLastMessageForCsTicket(ticketId.trim(), packet, expireMs, TimeUnit.MILLISECONDS);
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
                appKey,
                ticketId,
                packet.getPacketId(),
                message.getFromType(),
                serverTime != null ? serverTime : TimeUtil.currentTimeMillis(),
                route.agentType());
        String topic = MqConstant.MQ_CS_TICKET_ACTIVITY_TOPIC;
        String key = CsTicketActivityNotifyPayload.messageKey(appKey, ticketId);
        String json = JSON.toJSONString(body);
        DefaultRepository.INSTANCE.publishJsonAsync(topic, key, json, "CS ticket-activity MQ, ticketId=" + ticketId);
        if (log.isDebugEnabled()) {
            log.debug("CS ticket-activity 已投递 MQ, topic={}, ticketId={}, packetId={}",
                    topic, ticketId, packet.getPacketId());
        }
    }

    private static Long parseTicketId(CsImSessionRoute route) {
        String scopeId = ticketMessageScopeId(route);
        if (StringUtils.isBlank(scopeId)) {
            return null;
        }
        try {
            return Long.parseLong(scopeId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // 坐席 presence
    // -------------------------------------------------------------------------

    public static void notifyIfCsAgent(LoginClientInfo loginInfo, String eventType, String reason) {
        if (loginInfo == null || StringUtils.isBlank(loginInfo.getIdentity())) {
            return;
        }
        if (StringUtils.isBlank(eventType)) {
            log.warn("CS agent-presence 跳过：eventType 为空 agentId={}", loginInfo.getIdentity());
            return;
        }
        if (LoginScopeEnum.fromType(loginInfo.getScope()) != LoginScopeEnum.CS_AGENT) {
            return;
        }
        MessageServerProperties props = serverProperties();
        if (props == null || !props.isCsAgentPresenceEnabled()) {
            return;
        }
        String appKey = AppKeyUtil.defaultIfBlank(loginInfo.getAppKey());
        CsAgentPresenceNotifyPayload body = new CsAgentPresenceNotifyPayload(
                appKey,
                loginInfo.getIdentity(),
                loginInfo.getScope(),
                loginInfo.getDeviceType(),
                reason,
                TimeUtil.currentTimeMillis(),
                eventType);
        body.setLoginServerAddress(loginInfo.getLoginServerAddress());
        String topic = MqConstant.MQ_CS_AGENT_PRESENCE_TOPIC;
        String key = CsAgentPresenceNotifyPayload.messageKey(appKey, loginInfo.getIdentity());
        String json = JSON.toJSONString(body);
        DefaultRepository.INSTANCE.publishJsonAsync(
                topic, key, json,
                "CS agent-presence MQ, agentId=" + loginInfo.getIdentity()
                        + ", eventType=" + eventType + ", reason=" + reason);
        if (log.isDebugEnabled()) {
            log.debug("CS agent-presence 已投递 MQ, topic={}, agentId={}, eventType={}, reason={}",
                    topic, loginInfo.getIdentity(), eventType, reason);
        }
    }

    private static MessageServerProperties serverProperties() {
        if (MessageServerContext.messageProperties instanceof MessageServerProperties props) {
            return props;
        }
        return null;
    }
}
