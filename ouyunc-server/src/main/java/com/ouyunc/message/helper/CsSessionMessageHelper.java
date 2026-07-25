package com.ouyunc.message.helper;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageFromToTypeEnum;
import com.ouyunc.base.constant.enums.YesOrNo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.cs.CsImSessionRoute;
import org.apache.commons.lang3.StringUtils;

/**
 * 客服消息会话上下文。
 * <p>路由主键 = {@code ticketId}（消息 {@code correlationId}）；通道语义仍为访客↔入口，字段在路由 Hash 中。</p>
 */
public final class CsSessionMessageHelper {

    private CsSessionMessageHelper() {
    }

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

        int fromType = message.getFromType();
        if (fromType != MessageFromToTypeEnum.CS_AGENT.getType()
                && fromType != MessageFromToTypeEnum.CS_VISITOR.getType()) {
            return PrepareOutcome.reject("非客服访客/坐席身份，不能发送客服消息");
        }

        String ticketId = message.getCorrelationId();
        if (StringUtils.isBlank(ticketId)) {
            return PrepareOutcome.reject("客服消息必须携带 ticketId(correlationId)");
        }

        CsImSessionRoute route = DefaultRepository.INSTANCE.getCsImSessionRoute(appKey, ticketId);
        if (route == null) {
            return PrepareOutcome.reject("客服会话路由不存在，请先创单并写入路由");
        }
        if (StringUtils.isAnyBlank(route.ticketId(), route.userId(), route.serviceIdentity(), route.sessionId())) {
            return PrepareOutcome.reject("会话路由数据不完整");
        }
        if (!StringUtils.equals(route.ticketId(), ticketId)) {
            return PrepareOutcome.reject("correlationId 与路由 ticketId 不一致");
        }
        if (!route.isActive(YesOrNo.YES.getCode())) {
            return PrepareOutcome.reject("咨询单已关闭或不可收发消息");
        }
        if (StringUtils.isBlank(route.assigneeId())) {
            return PrepareOutcome.reject("咨询单尚未分配坐席");
        }

        if (fromType == MessageFromToTypeEnum.CS_AGENT.getType()) {
            if (!StringUtils.equals(from, route.assigneeId())) {
                return PrepareOutcome.reject("当前坐席无权在该会话发消息");
            }
            if (!StringUtils.equals(to, route.userId())) {
                return PrepareOutcome.reject("坐席 to 必须为访客 userId");
            }
            // 投递/外渠按入口 identity；对外通道语义仍是访客↔入口
            message.setFrom(route.serviceIdentity());
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

    public static void publishReject(Packet packet, String reason) {
        MessageServerContext.publishEvent(new MessageEvent(
                ExceptionEventPayload.of(ExceptionCodeEnum.CS_SESSION_ROUTE_ERROR, reason, packet),
                MessageEventTypeEnum.EXCEPTION), true);
    }
}
