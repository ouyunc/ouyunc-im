package com.ouyunc.message.processor.http.push;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.constant.enums.MessagePushStatusEnum;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.ContentSafetyResult;
import com.ouyunc.base.model.HttpResponseResult;
import com.ouyunc.base.model.MessagePushRequest;
import com.ouyunc.base.model.MessagePushResponse;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.http.HttpContext;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.processor.http.push.delivery.HttpPushDeliverySupport;
import com.ouyunc.message.safety.ContentSafetyFacade;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * HTTP 推送入口：校验通过后写入 PENDING，后台落库成功再 COMMITTED。
 * <p>{@code ACCEPTED}＝已受理（PENDING）；{@code DUPLICATE}＝已 COMMITTED；
 * {@code PROCESSING}＝同 messageId 仍在途；{@code RETRYABLE_FAILED}＝后台失败可重试。
 * 多接收人用 {@code messageId:to} 分键，避免局部成功被整键清掉。
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
        List<String> recipients = resolveRecipients(request);
        if (recipients.size() > 1) {
            return pushFanout(request, httpContext, recipients);
        }
        if (recipients.size() == 1 && StringUtils.isBlank(request.getTo())) {
            request.setTo(recipients.get(0));
        }
        return pushSingle(request, httpContext);
    }

    private static CompletionStage<HttpResponseResult<MessagePushResponse>> pushFanout(
            MessagePushRequest request, HttpContext httpContext, List<String> recipients)
            throws HttpPipelineException {
        HttpPushValidator.validateCommon(request, httpContext);
        String baseMessageId = request.getMessageId();
        CompletableFuture<HttpResponseResult<MessagePushResponse>> future = new CompletableFuture<>();
        try {
            ThreadPoolManager.httpPushVerifyExecutor().execute(() -> {
                try {
                    List<MessagePushResponse> items = new ArrayList<>(recipients.size());
                    String worstStatus = MessagePushStatusEnum.ACCEPTED.getCode();
                    String lastPacketId = null;
                    for (String to : recipients) {
                        request.setTo(to);
                        request.setMessageId(baseMessageId + ':' + to);
                        HttpResponseResult<MessagePushResponse> one = pushSingleSync(request, httpContext);
                        MessagePushResponse body = one != null ? one.getData() : null;
                        if (body != null) {
                            items.add(body);
                            lastPacketId = body.getPacketId();
                            worstStatus = worsePushStatus(worstStatus, body.getStatus());
                        }
                    }
                    request.setMessageId(baseMessageId);
                    MessagePushResponse aggregate = new MessagePushResponse();
                    aggregate.setMessageId(baseMessageId);
                    aggregate.setPacketId(lastPacketId);
                    aggregate.setStatus(worstStatus);
                    aggregate.setItems(items);
                    future.complete(HttpResponseResult.success(aggregate));
                } catch (HttpPipelineException ex) {
                    future.completeExceptionally(ex);
                } catch (Throwable t) {
                    log.error("HTTP 推送 toList 扇出异常, messageId={}", baseMessageId, t);
                    future.completeExceptionally(new HttpPipelineException(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                            HttpResponseCodeEnum.INTERNAL_SERVER_ERROR, "HTTP 推送受理失败"));
                }
            });
        } catch (RuntimeException ex) {
            throw new HttpPipelineException(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    HttpResponseCodeEnum.INTERNAL_SERVER_ERROR, "HTTP 推送受理失败：verify 任务提交异常");
        }
        return future;
    }

    private static CompletionStage<HttpResponseResult<MessagePushResponse>> pushSingle(
            MessagePushRequest request, HttpContext httpContext) throws HttpPipelineException {
        Packet packet = HttpPushValidator.validateAndPrepare(request, httpContext);
        return enqueueVerify(packet, httpContext.getAppKey(), request.getMessageId(), String.valueOf(packet.getPacketId()));
    }

    private static HttpResponseResult<MessagePushResponse> pushSingleSync(
            MessagePushRequest request, HttpContext httpContext) throws HttpPipelineException {
        Packet packet = MessagePushPacketConverter.convert(request, httpContext);
        HttpPushJwtAuth.validateResolvedPacketScope(httpContext, request, packet);
        HttpPushSupportedTypes.validate(packet);
        if (!IngressAuthSupport.INSTANCE.verify(packet, httpContext)) {
            throw new HttpPipelineException(HttpResponseStatus.UNAUTHORIZED, HttpResponseCodeEnum.UNAUTHORIZED,
                    "HTTP 推送鉴权失败");
        }
        return acceptAfterPreProcess(packet, httpContext.getAppKey(), request.getMessageId(),
                String.valueOf(packet.getPacketId()));
    }

    private static CompletionStage<HttpResponseResult<MessagePushResponse>> enqueueVerify(
            Packet packet, String appKey, String messageId, String packetIdStr) throws HttpPipelineException {
        HttpResponseResult<MessagePushResponse> early = respondIfCommitted(appKey, messageId);
        if (early != null) {
            return CompletableFuture.completedFuture(early);
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

    /**
     * 仅 COMMITTED 快速返回。PENDING 必须进入原子抢占脚本，由 Redis 时间判断是否允许接管。
     */
    private static HttpResponseResult<MessagePushResponse> respondIfCommitted(String appKey, String messageId) {
        PushIdempotencySupport.IdempotencyRecord record = PushIdempotencySupport.getRecord(appKey, messageId);
        if (record == null) {
            return null;
        }
        if (PushIdempotencySupport.STATE_COMMITTED.equals(record.state())) {
            return HttpResponseResult.success(buildResponse(messageId, record.packetId(),
                    MessagePushStatusEnum.DUPLICATE, null));
        }
        return null;
    }

    private static List<String> resolveRecipients(MessagePushRequest request) throws HttpPipelineException {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (request != null && request.getToList() != null) {
            for (String to : request.getToList()) {
                if (StringUtils.isNotBlank(to)) {
                    ids.add(to.trim());
                }
            }
        }
        if (request != null && StringUtils.isNotBlank(request.getTo())) {
            ids.add(request.getTo().trim());
        }
        if (ids.size() > MessageConstant.HTTP_PUSH_TO_LIST_MAX) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "toList 超过上限 " + MessageConstant.HTTP_PUSH_TO_LIST_MAX);
        }
        return new ArrayList<>(ids);
    }

    private static HttpResponseResult<MessagePushResponse> acceptAfterPreProcess(
            Packet packet, String appKey, String messageId, String packetIdStr) throws HttpPipelineException {
        // 与长连接管道一致：敏感词 MASK/REJECT，避免 HTTP Push 绕过
        applyContentSafetyOrThrow(packet);

        HttpPushProcessorDelegate.preProcessOrThrow(packet);

        int claim = PushIdempotencySupport.tryClaim(appKey, messageId, packetIdStr);
        if (claim == PushIdempotencySupport.CLAIM_COMMITTED) {
            HttpPushDeliverySupport.discardStashed(packet);
            PushIdempotencySupport.IdempotencyRecord record = PushIdempotencySupport.getRecord(appKey, messageId);
            String committedId = record != null ? record.packetId() : packetIdStr;
            return HttpResponseResult.success(buildResponse(messageId, committedId,
                    MessagePushStatusEnum.DUPLICATE, null));
        }
        if (claim == PushIdempotencySupport.CLAIM_PENDING) {
            HttpPushDeliverySupport.discardStashed(packet);
            return HttpResponseResult.success(buildResponse(messageId, packetIdStr,
                    MessagePushStatusEnum.PROCESSING, "同 messageId 正在处理，请稍后重试"));
        }
        if (claim != PushIdempotencySupport.CLAIM_ACQUIRED) {
            HttpPushDeliverySupport.discardStashed(packet);
            throw new HttpPipelineException(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    HttpResponseCodeEnum.INTERNAL_SERVER_ERROR, "HTTP 推送幂等占位失败");
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

        // ACCEPTED = PENDING 已写入，后台落库成功后才会 COMMITTED
        return HttpResponseResult.success(buildResponse(messageId, packetIdStr,
                MessagePushStatusEnum.ACCEPTED, null));
    }

    /**
     * 内容安全拒绝时返回 HTTP 400；MASK 已原地改写 packet.content，继续受理。
     * 检查异常时放行，避免误杀整条推送。
     *
     * @param packet 已组装的协议包
     */
    private static void applyContentSafetyOrThrow(Packet packet) throws HttpPipelineException {
        ContentSafetyResult result;
        try {
            result = ContentSafetyFacade.check(packet);
        } catch (Exception e) {
            log.error("HTTP 推送内容安全检查异常，放行以免误杀 message packetId={}",
                    packet == null ? null : packet.getPacketId(), e);
            return;
        }
        if (result != null && !result.isPassed()) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    ExceptionCodeEnum.CONTENT_SENSITIVE_REJECT.getMessage());
        }
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

    /** 扇出聚合：失败优先于处理中，处理中优先于已受理，已受理优先于重复。 */
    private static String worsePushStatus(String current, String next) {
        if (next == null) {
            return current;
        }
        if (rankPushStatus(next) > rankPushStatus(current)) {
            return next;
        }
        return current;
    }

    private static int rankPushStatus(String status) {
        if (MessagePushStatusEnum.RETRYABLE_FAILED.getCode().equals(status)) {
            return 4;
        }
        if (MessagePushStatusEnum.PROCESSING.getCode().equals(status)) {
            return 3;
        }
        if (MessagePushStatusEnum.ACCEPTED.getCode().equals(status)) {
            return 2;
        }
        if (MessagePushStatusEnum.DUPLICATE.getCode().equals(status)) {
            return 1;
        }
        return 0;
    }
}
