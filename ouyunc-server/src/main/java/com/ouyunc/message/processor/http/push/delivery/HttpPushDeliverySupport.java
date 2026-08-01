package com.ouyunc.message.processor.http.push.delivery;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.model.ClientInfo;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.cache.Cache;
import com.ouyunc.cache.local.caffeine.CaffeineLocalCache;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
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

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
     * 策略 {@code process} 统一订阅内部 Mono；失败只打日志。
     * <p>已 ACCEPTED 后不释放幂等，靠 TTL，避免同 messageId 双发。</p>
     */
    public static void subscribeDelivery(Packet packet, Mono<Boolean> delivery) {
        String messageId = packet != null && packet.getMessage() != null ? packet.getMessage().getId() : null;
        delivery.subscribe(
                ok -> {
                    if (!Boolean.TRUE.equals(ok)) {
                        log.warn("HTTP 推送后台投递未成功（幂等已保留）, messageId={}", messageId);
                    }
                },
                ex -> log.error("HTTP 推送后台投递异常（幂等已保留）, messageId={}", messageId, ex));
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

    public static void publishException(ExceptionCodeEnum code, String message, Packet packet) {
        // ACCEPTED 后不释放幂等
        MessageServerContext.publishEvent(new MessageEvent(
                ExceptionEventPayload.of(code, message, packet),
                MessageEventTypeEnum.EXCEPTION), true);
    }

    /**
     * 供 reactiveHandleOperation 异常回调：仅发布事件。
     */
    public static void publishExceptionEvent(MessageEvent event) {
        MessageServerContext.publishEvent(event, true);
    }

    /**
     * 释放幂等占位（仅占位成功后、提交后台失败时回滚，便于客户端重试）。
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
