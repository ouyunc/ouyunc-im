package com.ouyunc.message.processor.http.push;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqArchiveRouting;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.constant.enums.MessageSubmissionStatusEnum;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.ContentSafetyResult;
import com.ouyunc.base.model.HttpResponseResult;
import com.ouyunc.base.model.MessagePushExtra;
import com.ouyunc.base.model.MessagePushRequest;
import com.ouyunc.base.model.MessagePushResponse;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.http.HttpContext;
import com.ouyunc.message.helper.QosCommittedDeliverySupport;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.processor.http.push.delivery.HttpPushDeliverySupport;
import com.ouyunc.message.safety.ContentSafetyFacade;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;

/**
 * HTTP 推送入口：校验通过后同步完成 MQ confirm + Redis，在线扇出提交后再 COMMITTED。
 * <p>{@code ACCEPTED}＝已提交且本次在线扇出已交给写出；同一 messageId 再次进入只确认历史结果。
 * {@code REJECTED}＝业务明确拒绝；{@code RETRY_LATER}＝当前占位仍在处理；
 * {@code UNKNOWN}＝提交或在线扇出结果不确定，须用同一 messageId 重试。
 * 多接收人用 {@code messageId:to} 分键，按接收人复制请求后最多 16 路并行，避免局部成功被整键清掉，
 * 也不共享可变的 to / messageId。
 * 顺序：鉴权 → 历史结果查询 → preProcess（权限/规范化）→ 内容安全 → 幂等占位 → MQ+Redis；
 * preProcess 与管线均在 {@link ThreadPoolManager#httpPushVerifyExecutor()} 执行。</p>
 */
public final class InternalPacketIngressService {

    private static final Logger log = LoggerFactory.getLogger(InternalPacketIngressService.class);

    /** 同一批 toList 的并行度。虚拟线程池上分窗执行，避免 256 路同时打归档。 */
    private static final int HTTP_PUSH_FANOUT_PARALLELISM = 16;

    private InternalPacketIngressService() {
    }

    /**
     * @return 已完成的 Future（如幂等已提交），或 verify 池异步完成后的 Future
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
                    List<MessagePushResponse> items = pushRecipients(request, httpContext, recipients, baseMessageId);
                    String worstStatus = MessageSubmissionStatusEnum.ACCEPTED.name();
                    MessagePushResponse worstItem = null;
                    String lastPacketId = null;
                    for (MessagePushResponse body : items) {
                        if (body == null) {
                            continue;
                        }
                        lastPacketId = body.getPacketId();
                        if (worstItem == null || rankPushStatus(body.getStatus()) > rankPushStatus(worstItem.getStatus())) {
                            worstItem = body;
                        }
                        worstStatus = worsePushStatus(worstStatus, body.getStatus());
                    }
                    MessagePushResponse aggregate = new MessagePushResponse();
                    aggregate.setMessageId(baseMessageId);
                    aggregate.setPacketId(MessageSubmissionStatusEnum.ACCEPTED.name().equals(worstStatus) ? lastPacketId : null);
                    aggregate.setStatus(worstStatus);
                    if (worstItem != null) {
                        aggregate.setCode(worstItem.getCode());
                        aggregate.setDescription(worstItem.getDescription());
                    }
                    aggregate.setItems(items);
                    future.complete(HttpResponseResult.success(aggregate));
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

    /**
     * 按接收人顺序分窗受理。每项使用独立请求副本，单项失败记 UNKNOWN，不改原始请求。
     * 父任务已经占着 verify 池的一个准入名额，子任务再申请；申请失败立即记 UNKNOWN，不会在池里阻塞等待。
     */
    private static List<MessagePushResponse> pushRecipients(
            MessagePushRequest request, HttpContext httpContext, List<String> recipients, String baseMessageId) {
        List<MessagePushResponse> items = new ArrayList<>(recipients.size());
        ExecutorService executor = ThreadPoolManager.httpPushVerifyExecutor();
        for (int start = 0; start < recipients.size(); start += HTTP_PUSH_FANOUT_PARALLELISM) {
            int end = Math.min(recipients.size(), start + HTTP_PUSH_FANOUT_PARALLELISM);
            List<CompletableFuture<MessagePushResponse>> window = new ArrayList<>(end - start);
            for (int i = start; i < end; i++) {
                String to = recipients.get(i);
                String itemMessageId = baseMessageId + ':' + to;
                window.add(submitFanoutItem(executor, request, httpContext, baseMessageId, to, itemMessageId));
            }
            CompletableFuture.allOf(window.toArray(CompletableFuture[]::new)).join();
            for (CompletableFuture<MessagePushResponse> one : window) {
                items.add(one.join());
            }
        }
        return items;
    }

    private static CompletableFuture<MessagePushResponse> submitFanoutItem(
            ExecutorService executor, MessagePushRequest request, HttpContext httpContext,
            String baseMessageId, String to, String itemMessageId) {
        try {
            return CompletableFuture.supplyAsync(
                    () -> pushFanoutItem(request, httpContext, baseMessageId, to, itemMessageId), executor);
        } catch (Throwable submitError) {
            log.error("HTTP 批量推送单项提交失败, baseMessageId={} itemMessageId={} to={}",
                    baseMessageId, itemMessageId, to, submitError);
            return CompletableFuture.completedFuture(buildResponse(itemMessageId, null,
                    MessageSubmissionStatusEnum.UNKNOWN, "单项受理结果未知，请使用该 item messageId 重试"));
        }
    }

    private static MessagePushResponse pushFanoutItem(
            MessagePushRequest request, HttpContext httpContext,
            String baseMessageId, String to, String itemMessageId) {
        try {
            MessagePushRequest itemRequest = copyForRecipient(request, to, itemMessageId);
            HttpResponseResult<MessagePushResponse> one = pushSingleSync(itemRequest, httpContext);
            MessagePushResponse body = one != null ? one.getData() : null;
            if (body == null) {
                return buildResponse(itemMessageId, null, MessageSubmissionStatusEnum.UNKNOWN,
                        "单项受理结果未知，请使用该 item messageId 重试");
            }
            return body;
        } catch (Throwable itemError) {
            // 批量请求允许局部成功；单项异常必须转成逐项结果，不能丢弃此前已完成项。
            log.error("HTTP 批量推送单项异常, baseMessageId={} itemMessageId={} to={}",
                    baseMessageId, itemMessageId, to, itemError);
            return buildResponse(itemMessageId, null, MessageSubmissionStatusEnum.UNKNOWN,
                    "单项受理结果未知，请使用该 item messageId 重试");
        }
    }

    /** 扇出项不共享原请求。extra 的列表和扩展表各自复制，避免并行改写同一份 @/引用。 */
    private static MessagePushRequest copyForRecipient(MessagePushRequest source, String to, String itemMessageId) {
        MessagePushRequest copy = new MessagePushRequest();
        copy.setPushType(source.getPushType());
        copy.setPushChannel(source.getPushChannel());
        copy.setMessageId(itemMessageId);
        copy.setFrom(source.getFrom());
        copy.setFromType(source.getFromType());
        copy.setTo(to);
        copy.setToType(source.getToType());
        copy.setContent(source.getContent());
        copy.setContentType(source.getContentType());
        copy.setCorrelationId(source.getCorrelationId());
        copy.setDeviceType(source.getDeviceType());
        copy.setCreateTime(source.getCreateTime());
        copy.setExtra(copyExtra(source.getExtra()));
        return copy;
    }

    private static MessagePushExtra copyExtra(MessagePushExtra source) {
        if (source == null) {
            return null;
        }
        MessagePushExtra copy = new MessagePushExtra();
        if (source.getAt() != null) {
            copy.setAt(new ArrayList<>(source.getAt()));
        }
        if (source.getRef() != null) {
            copy.setRef(new ArrayList<>(source.getRef()));
        }
        copy.setQos(source.getQos());
        if (source.getExtensions() != null) {
            copy.setExtensions(new HashMap<>(source.getExtensions()));
        }
        return copy;
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
        return acceptAuthenticated(packet, httpContext.getAppKey(), request.getMessageId(),
                String.valueOf(packet.getPacketId()));
    }

    private static CompletionStage<HttpResponseResult<MessagePushResponse>> enqueueVerify(
            Packet packet, String appKey, String messageId, String packetIdStr) throws HttpPipelineException {
        CompletableFuture<HttpResponseResult<MessagePushResponse>> future = new CompletableFuture<>();
        try {
            ThreadPoolManager.httpPushVerifyExecutor().execute(() -> {
                try {
                    future.complete(acceptAuthenticated(packet, appKey, messageId, packetIdStr));
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

    private static HttpResponseResult<MessagePushResponse> acceptAuthenticated(
            Packet packet, String appKey, String messageId, String packetIdStr) throws HttpPipelineException {
        // HTTP 已完成身份鉴权；在 ref/@、客服身份和内容安全改写前固定原始请求指纹。
        packet.getMessage().ensureMetadata().ensureHttpPushClaim().setHttpPushPayloadHash(
                com.ouyunc.repository.support.QosIdempotencyHelper.payloadHash(packet.getMessage()));
        // 历史 HTTP COMMITTED 仅在整个管线及投递成功后写入，可直接确认。
        // 此时身份鉴权已完成；只读查询不会为未通过业务校验的新请求留下占位。
        HttpResponseResult<MessagePushResponse> previous = resolveHistoricalResult(packet, appKey, messageId);
        if (previous != null) {
            return previous;
        }
        // 首次请求与未完成请求仍须通过当前业务校验。
        try {
            HttpPushProcessorDelegate.preProcessOrThrow(packet);
            applyContentSafetyOrThrow(packet);
        } catch (HttpPipelineException ex) {
            // 入站鉴权错误保持 HTTP 错误；已有 messageId 的业务拒绝返回统一逐消息结果。
            HttpPushDeliverySupport.discardStashed(packet);
            if (ex.getStatus().code() == HttpResponseStatus.UNAUTHORIZED.code()) {
                throw ex;
            }
            // 前次执行可能在本次查询之后刚完成，业务校验拒绝前再确认一次，避免并发关单误报。
            HttpResponseResult<MessagePushResponse> completed = resolveHistoricalResult(packet, appKey, messageId);
            if (completed != null) {
                return completed;
            }
            MessageSubmissionStatusEnum rejectedStatus = ex.getStatus().code() >= HttpResponseStatus.INTERNAL_SERVER_ERROR.code()
                    ? MessageSubmissionStatusEnum.RETRY_LATER : MessageSubmissionStatusEnum.REJECTED;
            return HttpResponseResult.success(buildResponse(messageId, null, rejectedStatus, ex.getMessage()));
        }

        return claimAndExecute(packet, appKey, messageId, packetIdStr);
    }

    /** 先确认历史完成或恢复已提交聊天消息；返回 null 才进入当前业务校验。 */
    private static HttpResponseResult<MessagePushResponse> resolveHistoricalResult(
            Packet packet, String appKey, String messageId) {
        PushIdempotencySupport.ClaimResult previous = PushIdempotencySupport.lookup(
                appKey, messageId, packet.getMessage(), packet.getMessageType());
        return switch (previous.state()) {
            case PushIdempotencySupport.CLAIM_COMMITTED -> HttpResponseResult.success(buildResponse(
                    messageId, previous.canonicalPacketId(), MessageSubmissionStatusEnum.ACCEPTED, null));
            case PushIdempotencySupport.CLAIM_CONFLICT -> HttpResponseResult.success(buildResponse(
                    messageId, null, MessageSubmissionStatusEnum.REJECTED, ExceptionCodeEnum.MESSAGE_ID_CONFLICT.getMessage()));
            case PushIdempotencySupport.CLAIM_FAILED -> HttpResponseResult.success(buildResponse(
                    messageId, null, MessageSubmissionStatusEnum.UNKNOWN, "历史受理结果暂不可确认，请使用同一 messageId 重试"));
            default -> recoverCommittedChat(packet, appKey, messageId);
        };
    }

    /**
     * HTTP 尚未完成但 QoS 已提交时，只恢复首次持久化的普通聊天消息。
     * 判重同时校验原始指纹和消息类型；归档存在本身不能证明业务提交。
     * 控制消息仍走各自的目标快照恢复，禁止当作普通聊天再次投递。
     */
    private static HttpResponseResult<MessagePushResponse> recoverCommittedChat(
            Packet packet, String appKey, String messageId) {
        if ((packet.getMessageType() != MessageTypeEnum.CUSTOMER_SERVICE.getType()
                && packet.getMessageType() != MessageTypeEnum.ONE_2_ONE.getType()
                && packet.getMessageType() != MessageTypeEnum.GROUP.getType())
                || MqArchiveRouting.skipsSaveArchive(packet)) {
            return null;
        }
        try {
            if (!DefaultRepository.INSTANCE.checkCommittedForRecovery(packet, packet.getMessage().getFrom())) {
                return null;
            }
            Packet committed = QosCommittedDeliverySupport.loadCommittedPacket(DefaultRepository.INSTANCE, packet);
            // 沿用本次已鉴权请求的原始指纹；旧归档中的 HTTP owner 不得复用。
            committed.getMessage().ensureMetadata().ensureHttpPushClaim().setHttpPushPayloadHash(
                    packet.getMessage().getMetadata().getHttpPushClaim().getHttpPushPayloadHash());
            return claimAndExecute(committed, appKey, messageId, String.valueOf(committed.getPacketId()), true);
        } catch (RuntimeException error) {
            log.error("HTTP 已提交聊天消息恢复失败, messageId={}", messageId, error);
            return HttpResponseResult.success(buildResponse(messageId, null,
                    MessageSubmissionStatusEnum.UNKNOWN, "已提交消息恢复暂不可确认，请使用同一 messageId 重试"));
        }
    }

    /** 校验通过后才原子抢占；只读历史查询和当前执行之间的竞争由脚本再次裁决。 */
    private static HttpResponseResult<MessagePushResponse> claimAndExecute(
            Packet packet, String appKey, String messageId, String packetIdStr) {
        return claimAndExecute(packet, appKey, messageId, packetIdStr, false);
    }

    /** 恢复也必须取得 HTTP owner，避免与尚在执行的首次请求并发完成。 */
    private static HttpResponseResult<MessagePushResponse> claimAndExecute(
            Packet packet, String appKey, String messageId, String packetIdStr, boolean committedRecovery) {
        PushIdempotencySupport.ClaimResult claim = PushIdempotencySupport.tryClaim(
                appKey, messageId, packetIdStr, packet.getMessage(), packet.getMessageType());
        if (claim.state() == PushIdempotencySupport.CLAIM_CONFLICT) {
            HttpPushDeliverySupport.discardStashed(packet);
            return HttpResponseResult.success(buildResponse(messageId, null,
                    MessageSubmissionStatusEnum.REJECTED, ExceptionCodeEnum.MESSAGE_ID_CONFLICT.getMessage()));
        }
        if (claim.state() == PushIdempotencySupport.CLAIM_COMMITTED) {
            String committedId = claim.canonicalPacketId();
            alignCommittedPacketId(packet, committedId);
            HttpPushDeliverySupport.discardStashed(packet);
            return HttpResponseResult.success(buildResponse(messageId, committedId,
                    MessageSubmissionStatusEnum.ACCEPTED, null));
        }
        if (claim.state() == PushIdempotencySupport.CLAIM_PENDING) {
            HttpPushDeliverySupport.discardStashed(packet);
            return HttpResponseResult.success(buildResponse(messageId, packetIdStr,
                    MessageSubmissionStatusEnum.RETRY_LATER, "同 messageId 正在处理，请稍后重试"));
        }
        if (claim.state() != PushIdempotencySupport.CLAIM_ACQUIRED) {
            HttpPushDeliverySupport.discardStashed(packet);
            return HttpResponseResult.success(buildResponse(messageId, null,
                    MessageSubmissionStatusEnum.UNKNOWN, "幂等占位结果未知，请使用同一 messageId 核对或重试"));
        }
        packetIdStr = claim.canonicalPacketId();
        alignCommittedPacketId(packet, packetIdStr);
        packet.getMessage().ensureMetadata().ensureHttpPushClaim().setHttpPushPayloadHash(claim.payloadHash());
        packet.getMessage().ensureMetadata().ensureHttpPushClaim().setHttpPushOwnerToken(claim.ownerToken());

        return executeClaimed(packet, messageId, packetIdStr, committedRecovery);
    }

    /** 仅当前 owner 执行受理管线，成功后才写入 HTTP COMMITTED。 */
    private static HttpResponseResult<MessagePushResponse> executeClaimed(
            Packet packet, String messageId, String packetIdStr, boolean committedRecovery) {
        try {
            boolean ok = committedRecovery ? HttpPushProcessorDelegate.replayCommitted(packet)
                    : HttpPushProcessorDelegate.runPipeline(packet);
            if (!ok) {
                HttpPushDeliverySupport.discardStashed(packet);
                HttpPushDeliverySupport.markRetryableFailed(packet);
                return HttpResponseResult.success(buildResponse(messageId, packetIdStr,
                        MessageSubmissionStatusEnum.UNKNOWN, "MQ 或热写失败，请使用同一 messageId 重试"));
            }
            if (!HttpPushDeliverySupport.commitIdempotency(packet)) {
                log.warn("HTTP 推送管线成功但幂等 COMMIT 结果未知, messageId={}", messageId);
                return HttpResponseResult.success(buildResponse(messageId, packetIdStr,
                        MessageSubmissionStatusEnum.UNKNOWN, "已写入但幂等提交结果未知，请使用同一 messageId 查询或重试"));
            }
            // ACCEPTED = MQ confirm + Redis 已提交，且本次在线扇出已交给写出/QoS。离线不算失败。
            return HttpResponseResult.success(buildResponse(messageId, packetIdStr,
                    MessageSubmissionStatusEnum.ACCEPTED, null));
        } catch (RuntimeException ex) {
            HttpPushDeliverySupport.discardStashed(packet);
            HttpPushDeliverySupport.forceReleaseIdempotencyClaim(packet);
            log.error("HTTP 推送管线触发失败, messageId={}", messageId, ex);
            return HttpResponseResult.success(buildResponse(messageId, null,
                    MessageSubmissionStatusEnum.UNKNOWN, "HTTP 推送受理结果未知，请使用同一 messageId 核对或重试"));
        }
    }

    /**
     * 内容安全拒绝时返回 HTTP 400；MASK 已原地改写 packet.content，继续受理。
     * 检查异常遵循租户 ingressFailOpen 策略，暂缓时返回可重试结果。
     *
     * @param packet 已组装的协议包
     */
    private static void applyContentSafetyOrThrow(Packet packet) throws HttpPipelineException {
        ContentSafetyResult result;
        try {
            result = ContentSafetyFacade.check(packet);
        } catch (Exception e) {
            if (ContentSafetyFacade.allowOnFailure(packet, e)) {
                return;
            }
            throw new HttpPipelineException(HttpResponseStatus.SERVICE_UNAVAILABLE,
                    HttpResponseCodeEnum.INTERNAL_SERVER_ERROR, "内容安全检查暂不可用，请使用同一 messageId 重试");
        }
        if (result != null && !result.isPassed()) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    ExceptionCodeEnum.CONTENT_SENSITIVE_REJECT.getMessage());
        }
    }

    private static void alignCommittedPacketId(Packet packet, String committedId) {
        if (packet == null || StringUtils.isBlank(committedId)) {
            return;
        }
        try {
            packet.setPacketId(Long.parseLong(committedId));
        } catch (NumberFormatException ex) {
            log.warn("HTTP COMMITTED packetId 无法解析, packetId={}", committedId);
        }
    }

    private static MessagePushResponse buildResponse(String messageId, String packetId,
                                                     MessageSubmissionStatusEnum status, String errorMessage) {
        MessagePushResponse response = new MessagePushResponse();
        response.setMessageId(messageId);
        response.setPacketId(status == MessageSubmissionStatusEnum.ACCEPTED ? packetId : null);
        response.setStatus(status.name());
        if (status == MessageSubmissionStatusEnum.REJECTED) {
            response.setCode(ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT.getCode());
        } else if (status != MessageSubmissionStatusEnum.ACCEPTED) {
            response.setCode(ExceptionCodeEnum.UNKNOWN_ERROR.getCode());
        }
        response.setDescription(errorMessage);
        return response;
    }

    /** 扇出聚合按最不确定的逐接收人结果返回，详细状态保留在 items。 */
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
        if (MessageSubmissionStatusEnum.REJECTED.name().equals(status)) {
            return 4;
        }
        if (MessageSubmissionStatusEnum.UNKNOWN.name().equals(status)) {
            return 3;
        }
        if (MessageSubmissionStatusEnum.RETRY_LATER.name().equals(status)) {
            return 2;
        }
        if (MessageSubmissionStatusEnum.ACCEPTED.name().equals(status)) {
            return 1;
        }
        return 0;
    }
}
