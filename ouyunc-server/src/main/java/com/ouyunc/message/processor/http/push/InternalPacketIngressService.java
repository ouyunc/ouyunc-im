package com.ouyunc.message.processor.http.push;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.HttpResponseResult;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.base.model.MessagePushRequest;
import com.ouyunc.base.model.MessagePushResponse;
import com.ouyunc.base.constant.enums.MessagePushStatusEnum;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.http.HttpContext;
import com.ouyunc.message.http.HttpPipelineException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP 推送统一入口：前置校验 → 幂等 → {@link HttpPushProcessorDelegate} 落库并推送。
 */
public final class InternalPacketIngressService {

    private static final Logger log = LoggerFactory.getLogger(InternalPacketIngressService.class);

    private InternalPacketIngressService() {
    }

    public static HttpResponseResult<MessagePushResponse> push(MessagePushRequest request, HttpContext httpContext)
            throws HttpPipelineException {
        Packet packet = HttpPushValidator.validateAndPrepare(request, httpContext);

        String appKey = httpContext.getAppKey();
        String messageId = request.getMessageId();
        String packetIdStr = String.valueOf(packet.getPacketId());
        if (!PushIdempotencySupport.tryClaim(appKey, messageId, packetIdStr)) {
            String existingPacketId = PushIdempotencySupport.findClaimedPacketId(appKey, messageId);
            return HttpResponseResult.success(buildResponse(messageId,
                    existingPacketId != null ? existingPacketId : packetIdStr,
                    MessagePushStatusEnum.DUPLICATE,
                    probeRecipientOnline(appKey, packet)));
        }

        MessagePushResponse response = buildResponse(messageId, packetIdStr, MessagePushStatusEnum.ACCEPTED,
                probeRecipientOnline(appKey, packet));

        Runnable task = () -> {
            try {
                HttpPushProcessorDelegate.delegate(packet);
            } catch (Exception ex) {
                log.error("HTTP 推送投递异常, messageId={}", packet.getMessage().getId(), ex);
                MessageServerContext.publishEvent(new MessageEvent(
                        ExceptionEventPayload.of(ExceptionCodeEnum.UNKNOWN_ERROR, ex.getMessage(), packet),
                        MessageEventTypeEnum.EXCEPTION), true);
            }
        };
        if (MessageServerContext.serverProperties().isHttpPushAsync()) {
            ThreadPoolManager.messageProcessorExecutor().execute(task);
        } else {
            task.run();
        }
        return HttpResponseResult.success(response);
    }

    private static Boolean probeRecipientOnline(String appKey, Packet packet) {
        if (packet.getMessage() == null || StringUtils.isBlank(packet.getMessage().getTo())) {
            return null;
        }
        String to = packet.getMessage().getTo();
        if (MessageConstant.SPLAT.equals(to)) {
            return ClientHelper.connections(appKey) > 0 ? Boolean.TRUE : Boolean.FALSE;
        }
        return CollectionUtils.isNotEmpty(ClientHelper.onlineAll(appKey, to));
    }

    private static MessagePushResponse buildResponse(String messageId, String packetId,
                                                     MessagePushStatusEnum status, Boolean recipientOnline) {
        MessagePushResponse response = new MessagePushResponse();
        response.setMessageId(messageId);
        response.setPacketId(packetId);
        response.setStatus(status.getCode());
        response.setRecipientOnline(recipientOnline);
        return response;
    }
}
