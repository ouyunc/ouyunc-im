package com.ouyunc.message.processor.http.push.delivery;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.model.ClientInfo;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.cache.Cache;
import com.ouyunc.cache.local.caffeine.CaffeineLocalCache;
import com.ouyunc.core.exception.ExceptionReporter;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.helper.MessageRefHelper;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.processor.http.push.HttpPushFailures;
import com.ouyunc.message.processor.http.push.PushIdempotencySupport;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ouyunc.repository.cs.CsImSessionRoute;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 推送投递公共能力。
 */
public final class HttpPushDeliverySupport {

    private static final Logger log = LoggerFactory.getLogger(HttpPushDeliverySupport.class);

    /** preProcess 准备好的客服路由，供同请求 process 复用（避免二次 prepare）。 */
    private static final Cache<Long, CsImSessionRoute> CS_ROUTE_BY_PACKET_ID =  new CaffeineLocalCache<>("localClientInfoCache", Caffeine.newBuilder()
    .expireAfterWrite(NumberConstant.NUMBER_30, TimeUnit.SECONDS)
    .build(new CacheLoader<>() {
        @Override
        public @Nullable CsImSessionRoute load(Long packetId) throws Exception {
            return null;
        }
    }));

    /** preProcess 校验过的群成员，供同请求 process 复用（以受理时刻为准）。 */
    private static final Cache<Long, Set<String>> GROUP_MEMBERS_BY_PACKET_ID =  new CaffeineLocalCache<>("localClientInfoCache", Caffeine.newBuilder()
    .expireAfterWrite(NumberConstant.NUMBER_30, TimeUnit.SECONDS)
    .build(new CacheLoader<>() {
        @Override
        public @Nullable Set<String> load(Long packetId) throws Exception {
            return null;
        }
    }));

    private HttpPushDeliverySupport() {
    }

    public static void stashCsRoute(Packet packet, CsImSessionRoute route) {
        if (packet == null || route == null) {
            return;
        }
        CS_ROUTE_BY_PACKET_ID.put(packet.getPacketId(), route);
    }

    /** 取出并移除；无则 null。 */
    public static CsImSessionRoute takeCsRoute(Packet packet) {
        if (packet == null) {
            return null;
        }
        return CS_ROUTE_BY_PACKET_ID.asMap().remove(packet.getPacketId());
    }

    public static void stashGroupMembers(Packet packet, Set<String> members) {
        if (packet == null || members == null || members.isEmpty()) {
            return;
        }
        GROUP_MEMBERS_BY_PACKET_ID.put(packet.getPacketId(), members);
    }

    /** 取出并移除；无则 null。 */
    public static Set<String> takeGroupMembers(Packet packet) {
        if (packet == null) {
            return null;
        }
        return GROUP_MEMBERS_BY_PACKET_ID.asMap().remove(packet.getPacketId());
    }

    /** 占位失败或未进入 process 时丢弃 preProcess 缓存，避免泄漏。 */
    public static void discardStashed(Packet packet) {
        takeCsRoute(packet);
        takeGroupMembers(packet);
    }

    /**
     * 策略异步订阅内部 Mono（仅兼容旧 fire-and-forget 入口）。
     * <p>正式路径由 {@link com.ouyunc.message.processor.http.push.HttpPushProcessorDelegate#runPipeline}
     * 同步等待后调用 {@link #commitIdempotency}。</p>
     */
    public static void subscribeDelivery(Packet packet, Mono<Boolean> delivery) {
        String messageId = packet != null && packet.getMessage() != null ? packet.getMessage().getId() : null;
        delivery.subscribe(
                ok -> {
                    if (Boolean.TRUE.equals(ok)) {
                        if (!commitIdempotency(packet)) {
                            log.warn("HTTP 推送落库成功但幂等提交失败, messageId={}", messageId);
                        }
                    } else {
                        markRetryableFailed(packet);
                        log.warn("HTTP 推送后台投递未成功（已标 RETRYABLE_FAILED）, messageId={}", messageId);
                    }
                },
                ex -> {
                    markRetryableFailed(packet);
                    log.error("HTTP 推送后台投递异常（已标 RETRYABLE_FAILED）, messageId={}", messageId, ex);
                });
    }

    /** 校验并规范化 message.ref；失败抛 403（应在 preProcess 调用）。 */
    public static void requireValidMessageRef(Packet packet) throws HttpPipelineException {
        Message message = packet.getMessage();
        if (message == null || CollectionUtils.isEmpty(message.getRef())) {
            return;
        }
        try {
            message.setRef(MessageRefHelper.normalizeAndValidate(message.getRef()));
        } catch (IllegalArgumentException ex) {
            throw HttpPushFailures.forbidden(packet, ExceptionCodeEnum.MESSAGE_REF_INVALID_ERROR, ex.getMessage());
        }
    }

    /**
     * HTTP 推送侧异常上报；按错误码区分业务/系统。
     */
    public static void publishException(ExceptionCodeEnum code, String message, Packet packet) {
        String scene = "HttpPushDeliverySupport.publishException";
        if (code == null) {
            ExceptionReporter.reportSystem(ExceptionCodeEnum.UNKNOWN_ERROR, message, scene, packet);
            return;
        }
        String name = code.name();
        boolean business = name.startsWith("ILLEGAL_")
                || name.startsWith("CONTENT_MEDIA_")
                || name.startsWith("REQUEST_SESSION_")
                || name.equals("GROUP_MEMBER_NOT_EXIST_ERROR")
                || name.equals("GROUP_NOT_EXIST")
                || name.equals("USER_NOT_EXIST")
                || name.equals("MESSAGE_CONTENT_TYPE_ERROR")
                || name.equals("MESSAGE_TYPE_ERROR")
                || name.equals("MESSAGE_REF_INVALID_ERROR")
                || name.equals("HTTP_PUSH_BUSINESS_REJECT")
                || name.equals("CS_SESSION_ROUTE_ERROR")
                || name.endsWith("_VERIFY_ERROR");
        if (business) {
            ExceptionReporter.reportBusiness(code, message, scene, packet);
        } else {
            ExceptionReporter.reportSystem(code, message, scene, packet);
        }
    }

    /**
     * 释放 PENDING 占位（投递触发失败、尚未进入后台 Mono 时回滚）。
     */
    public static void forceReleaseIdempotencyClaim(Packet packet) {
        if (packet == null || packet.getMessage() == null) {
            return;
        }
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String appKey = metadata != null ? metadata.getAppKey() : null;
        String messageId = message.getId();
        String packetId = String.valueOf(packet.getPacketId());
        if (StringUtils.isAnyBlank(appKey, messageId)) {
            return;
        }
        try {
            boolean released = PushIdempotencySupport.releaseIfOwned(appKey, messageId, packetId);
            if (!released) {
                log.debug("HTTP 推送幂等释放未命中, appKey={}, messageId={}, packetId={}",
                        appKey, messageId, packetId);
            }
        } catch (Exception ex) {
            log.warn("释放 HTTP 推送幂等占位失败, appKey={}, messageId={}", appKey, messageId, ex);
        }
    }

    /** 同步管线成功后提交幂等（PENDING → COMMITTED）。 */
    public static boolean commitIdempotency(Packet packet) {
        IdempotencyCoords coords = resolveCoords(packet);
        if (coords == null) {
            return false;
        }
        try {
            return PushIdempotencySupport.commit(coords.appKey(), coords.messageId(), coords.packetId());
        } catch (Exception ex) {
            log.warn("HTTP 推送幂等 COMMIT 失败, messageId={}", coords.messageId(), ex);
            return false;
        }
    }

    public static void markRetryableFailed(Packet packet) {
        IdempotencyCoords coords = resolveCoords(packet);
        if (coords == null) {
            return;
        }
        try {
            boolean marked = PushIdempotencySupport.markRetryableFailed(
                    coords.appKey(), coords.messageId(), coords.packetId());
            if (!marked) {
                log.debug("HTTP 推送标记 RETRYABLE_FAILED 未命中, messageId={}", coords.messageId());
            }
        } catch (Exception ex) {
            log.warn("HTTP 推送标记 RETRYABLE_FAILED 失败, messageId={}", coords.messageId(), ex);
        }
    }

    private static IdempotencyCoords resolveCoords(Packet packet) {
        if (packet == null || packet.getMessage() == null) {
            return null;
        }
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String appKey = metadata != null ? metadata.getAppKey() : null;
        String messageId = message.getId();
        if (StringUtils.isAnyBlank(appKey, messageId)) {
            return null;
        }
        return new IdempotencyCoords(appKey, messageId, String.valueOf(packet.getPacketId()));
    }

    private record IdempotencyCoords(String appKey, String messageId, String packetId) {
    }

    /** HTTP 模拟用户默认多端同步；若本地有登录配置则尊重 selfSync。 */
    public static boolean shouldSelfSync(String appKey, String identity) {
        ClientInfo clientInfo = MessageServerContext.localClientInfo(appKey, identity);
        if (clientInfo == null) {
            return true;
        }
        return Boolean.TRUE.equals(clientInfo.getSelfSync());
    }

    public static void syncSenderOnlineDevices(Packet packet, String identity) {
        if (packet == null || packet.getMessage() == null || packet.getMessage().getMetadata() == null
                || StringUtils.isBlank(identity)) {
            return;
        }
        String appKey = packet.getMessage().getMetadata().getAppKey();
        if (!shouldSelfSync(appKey, identity)) {
            return;
        }
        List<LoginClientInfo> clients = ClientHelper.onlineAll(appKey, identity);
        if (CollectionUtils.isNotEmpty(clients)) {
            MessageHelper.asyncSendMessage(packet, clients);
        }
    }
}
