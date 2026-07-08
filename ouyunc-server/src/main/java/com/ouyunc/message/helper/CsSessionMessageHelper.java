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
 * 客服消息会话上下文：channel Route 校验；消息写入 ticket scope 见 {@link com.ouyunc.repository.cs.CsMessageScopeHelper}。
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

        String sessionId = IdentityUtil.sessionId(from, to);
        CsImSessionRoute route = DefaultRepository.INSTANCE.getCsImSessionRoute(appKey, sessionId);
        if (route == null) {
            return PrepareOutcome.reject("客服会话路由不存在，请先创单并写入路由");
        }
        if (StringUtils.isAnyBlank(route.ticketId(), route.userId(), route.serviceIdentity(), route.sessionId())) {
            return PrepareOutcome.reject("会话路由数据不完整");
        }
        if (!StringUtils.equals(route.sessionId(), sessionId)) {
            return PrepareOutcome.reject("会话路由 sessionId 与 from/to 不一致");
        }
        if (!route.isActive(YesOrNo.YES.getCode())) {
            return PrepareOutcome.reject("咨询单已关闭或不可收发消息");
        }
        if (StringUtils.isBlank(route.assigneeId())) {
            return PrepareOutcome.reject("咨询单尚未分配坐席");
        }

        if (!matchesSessionEndpoints(from, to, route)) {
            return PrepareOutcome.reject("from/to 与咨询单会话端点不一致");
        }

        int fromType = message.getFromType();
        if (fromType != MessageFromToTypeEnum.CS_AGENT.getType()
                && fromType != MessageFromToTypeEnum.CS_VISITOR.getType()) {
            return PrepareOutcome.reject("非客服访客/坐席身份，不能发送客服消息");
        }
        if (fromType == MessageFromToTypeEnum.CS_AGENT.getType()) {
            if (!StringUtils.equals(from, route.assigneeId())) {
                return PrepareOutcome.reject("当前坐席无权在该会话发消息");
            }
            message.setFrom(route.serviceIdentity());
        } else {
            if (!StringUtils.equals(from, route.userId()) || !StringUtils.equals(to, route.serviceIdentity())) {
                return PrepareOutcome.reject("访客 from/to 与咨询单不一致");
            }
        }

        if (StringUtils.isBlank(message.getCorrelationId())) {
            message.setCorrelationId(route.ticketId());
        } else if (!StringUtils.equals(message.getCorrelationId(), route.ticketId())) {
            return PrepareOutcome.reject("correlationId 与咨询单 ticketId 不一致");
        }

        return PrepareOutcome.ok(route);
    }

    private static boolean matchesSessionEndpoints(String from, String to, CsImSessionRoute route) {
        String userId = route.userId();
        String serviceIdentity = route.serviceIdentity();
        if (StringUtils.isAnyBlank(userId, serviceIdentity)) {
            return false;
        }
        boolean visitorSend = StringUtils.equals(from, userId) && StringUtils.equals(to, serviceIdentity);
        boolean agentSendBeforeRewrite = StringUtils.equals(from, route.assigneeId()) && StringUtils.equals(to, userId);
        boolean serviceSend = StringUtils.equals(from, serviceIdentity) && StringUtils.equals(to, userId);
        return visitorSend || agentSendBeforeRewrite || serviceSend;
    }

    public static void publishReject(Packet packet, String reason) {
        MessageServerContext.publishEvent(new MessageEvent(
                ExceptionEventPayload.of(ExceptionCodeEnum.CS_SESSION_ROUTE_ERROR, reason, packet),
                MessageEventTypeEnum.EXCEPTION), true);
    }
}
