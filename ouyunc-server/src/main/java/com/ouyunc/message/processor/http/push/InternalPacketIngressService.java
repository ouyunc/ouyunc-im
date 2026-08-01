package com.ouyunc.message.processor.http.push;

import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.constant.enums.MessagePushStatusEnum;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.HttpResponseResult;
import com.ouyunc.base.model.MessagePushRequest;
import com.ouyunc.base.model.MessagePushResponse;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.http.HttpContext;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.processor.http.push.delivery.HttpPushDeliverySupport;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * HTTP 推送入口（方案 1）：校验通过后再幂等占位，状态仅「无 / 已成功」。
 * <p>{@code ACCEPTED}/{@code DUPLICATE} 视为成功；占位冲突且尚无成功记录时 {@code PROCESSING}（可重试）。
 * preProcess 与触发投递均在 {@link ThreadPoolManager#httpPushVerifyExecutor()} 执行。</p>
 */
public final class InternalPacketIngressService {

    private static final Logger log = LoggerFactory.getLogger(InternalPacketIngressService.class);

    private InternalPacketIngressService() {
    }

    /**
     * @return 已完成的 Future（如 DUPLICATE），或 verify 池异步完成后的 Future
     */
    public static CompletionStage<HttpResponseResult<MessagePushResponse>> push(
            MessagePushRequest request, HttpContext httpContext) throws HttpPipelineException {
        Packet packet = HttpPushValidator.validateAndPrepare(request, httpContext);

        String appKey = httpContext.getAppKey();
        String messageId = request.getMessageId();
        String packetIdStr = String.valueOf(packet.getPacketId());

        String existing = PushIdempotencySupport.getPacketId(appKey, messageId);
        if (existing != null) {
            return CompletableFuture.completedFuture(HttpResponseResult.success(
                    buildResponse(messageId, existing, MessagePushStatusEnum.DUPLICATE, null)));
        }

        CompletableFuture<HttpResponseResult<MessagePushResponse>> future = new CompletableFuture<>();
        try {
            ThreadPoolManager.httpPushVerifyExecutor().execute(() -> {
                try {
                    future.complete(acceptAfterPreProcess(packet, appKey, messageId, packetIdStr));
                } catch (HttpPipelineException ex) {
                    future.completeExceptionally(ex);
                } catch (Throwable t) {
                    log.error("HTTP 推送 verify 阶段异常, messageId={}", messageId, t);
                    future.completeExceptionally(new HttpPipelineException(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                            HttpResponseCodeEnum.INTERNAL_SERVER_ERROR, "HTTP 推送受理失败"));
                }
            });
        } catch (RuntimeException ex) {
            log.error("HTTP 推送提交 verify 池失败, messageId={}", messageId, ex);
            throw new HttpPipelineException(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    HttpResponseCodeEnum.INTERNAL_SERVER_ERROR, "HTTP 推送受理失败：verify 任务提交异常");
        }
        return future;
    }

    private static HttpResponseResult<MessagePushResponse> acceptAfterPreProcess(
            Packet packet, String appKey, String messageId, String packetIdStr) throws HttpPipelineException {
        HttpPushProcessorDelegate.preProcessOrThrow(packet);

        if (!PushIdempotencySupport.tryClaim(appKey, messageId, packetIdStr)) {
            HttpPushDeliverySupport.discardStashed(packet);
            String claimed = PushIdempotencySupport.getPacketId(appKey, messageId);
            if (StringUtils.isNotBlank(claimed)) {
                return HttpResponseResult.success(buildResponse(messageId, claimed,
                        MessagePushStatusEnum.DUPLICATE, null));
            }
            return HttpResponseResult.success(buildResponse(messageId, packetIdStr,
                    MessagePushStatusEnum.PROCESSING, "同 messageId 受理冲突，请稍后重试"));
        }

        try {
            HttpPushProcessorDelegate.delegate(packet);
        } catch (RuntimeException ex) {
            HttpPushDeliverySupport.discardStashed(packet);
            HttpPushDeliverySupport.forceReleaseIdempotencyClaim(packet);
            log.error("HTTP 推送投递触发失败, messageId={}", messageId, ex);
            throw new HttpPipelineException(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    HttpResponseCodeEnum.INTERNAL_SERVER_ERROR, "HTTP 推送受理失败：投递触发异常");
        }

        return HttpResponseResult.success(buildResponse(messageId, packetIdStr,
                MessagePushStatusEnum.ACCEPTED, null));
    }

    private static MessagePushResponse buildResponse(String messageId, String packetId,
                                                     MessagePushStatusEnum status, String errorMessage) {
        MessagePushResponse response = new MessagePushResponse();
        response.setMessageId(messageId);
        response.setPacketId(packetId);
        response.setStatus(status.getCode());
        response.setErrorMessage(errorMessage);
        return response;
    }
}
