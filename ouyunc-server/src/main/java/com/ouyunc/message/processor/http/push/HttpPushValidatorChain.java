package com.ouyunc.message.processor.http.push;

import com.ouyunc.base.constant.enums.PushTypeEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.validator.BlackListValidator;
import com.ouyunc.message.validator.FriendShieldValidator;
import com.ouyunc.message.validator.FriendValidator;
import com.ouyunc.message.validator.FromToValidator;
import com.ouyunc.message.validator.GroupSilenceValidator;
import com.ouyunc.message.validator.PermissionValidator;
import com.ouyunc.message.validator.ReactiveValidator;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP 推送业务校验链（模拟用户）：供各策略在 {@code preProcess} 中同步调用；
 * 不通过则直接抛 {@link HttpPipelineException}。
 */
public final class HttpPushValidatorChain {

    private static final Logger log = LoggerFactory.getLogger(HttpPushValidatorChain.class);
    private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(5);

    private HttpPushValidatorChain() {
    }

    public static void verifyOne2One(Packet packet) throws HttpPipelineException {
        runChecks(buildOne2OneChecks(packet), packet);
    }

    public static void verifyGroup(Packet packet) throws HttpPipelineException {
        runChecks(buildGroupChecks(packet), packet);
    }

    public static void verifyCustomerService(Packet packet) throws HttpPipelineException {
        runChecks(buildCsChecks(), packet);
    }

    public static void verifyServerNotify(Packet packet) throws HttpPipelineException {
        runChecks(buildServerNotifyChecks(packet), packet);
    }

    /**
     * 顺序执行校验；首个拒绝原因非空则 403；校验执行异常则 500。
     */
    private static void runChecks(List<RejectCheck> checks, Packet packet) throws HttpPipelineException {
        if (checks.isEmpty()) {
            return;
        }
        String rejectReason;
        try {
            rejectReason = Flux.fromIterable(checks)
                    .concatMap(check -> check.shouldReject.verify(packet, null)
                            .flatMap(reject -> {
                                if (Boolean.TRUE.equals(reject)) {
                                    log.warn("HTTP 推送校验未通过: {}, packetId={}, messageId={}",
                                            check.reason, packet.getPacketId(), packet.getMessage().getId());
                                    return Mono.just(check.reason);
                                }
                                return Mono.empty();
                            }))
                    .next()
                    .block(VERIFY_TIMEOUT);
        } catch (RuntimeException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            log.error("HTTP 推送校验异常: {}", cause.getMessage(), cause);
            throw HttpPushFailures.serverError(packet, HttpPushFailures.formatError(cause));
        }
        if (StringUtils.isNotBlank(rejectReason)) {
            throw HttpPushFailures.forbidden(packet, rejectReason);
        }
    }

    private static List<RejectCheck> buildOne2OneChecks(Packet packet) {
        List<RejectCheck> checks = new ArrayList<>();
        checks.add(new RejectCheck("权限不足，无法发送该类型消息", PermissionValidator.INSTANCE.negate()));
        if (!skipUserRelationForSystem(packet)) {
            checks.add(new RejectCheck("双方不是好友，无法发送消息", FriendValidator.INSTANCE.negate()));
            checks.add(new RejectCheck("对方已拉黑，无法发送消息", BlackListValidator.INSTANCE));
            checks.add(new RejectCheck("对方已屏蔽，无法发送消息", FriendShieldValidator.INSTANCE));
        }
        checks.add(new RejectCheck("发送方与接收方不能相同", FromToValidator.INSTANCE));
        return checks;
    }

    private static List<RejectCheck> buildGroupChecks(Packet packet) {
        List<RejectCheck> checks = new ArrayList<>();
        checks.add(new RejectCheck("权限不足，无法发送该类型消息", PermissionValidator.INSTANCE.negate()));
        checks.add(new RejectCheck("发送方与接收方不能相同", FromToValidator.INSTANCE));
        if (!skipUserRelationForSystem(packet)) {
            checks.add(new RejectCheck("对方已拉黑，无法发送消息", BlackListValidator.INSTANCE));
            checks.add(new RejectCheck("群已全体禁言或发送方被禁言", GroupSilenceValidator.INSTANCE));
            // 群成员由 GroupHttpPushDeliveryStrategy.preProcess 一次拉取全量校验（避免与 GroupUserValidator 重复查）
        }
        return checks;
    }

    private static List<RejectCheck> buildCsChecks() {
        List<RejectCheck> checks = new ArrayList<>();
        checks.add(new RejectCheck("权限不足，无法发送该类型消息", PermissionValidator.INSTANCE.negate()));
        checks.add(new RejectCheck("发送方与接收方不能相同", FromToValidator.INSTANCE));
        return checks;
    }

    private static List<RejectCheck> buildServerNotifyChecks(Packet packet) {
        List<RejectCheck> checks = new ArrayList<>();
        checks.add(new RejectCheck("权限不足，无法发送该类型消息", PermissionValidator.INSTANCE.negate()));
        if (!isBroadcast(packet)) {
            checks.add(new RejectCheck("发送方与接收方不能相同", FromToValidator.INSTANCE));
        }
        return checks;
    }

    private static boolean skipUserRelationForSystem(Packet packet) {
        return IngressPacketHelper.isSystemLikeSender(packet.getMessage())
                && MessageServerContext.serverProperties().isHttpPushSkipFriendCheckForSystem();
    }

    private static boolean isBroadcast(Packet packet) {
        Metadata metadata = packet.getMessage().getMetadata();
        if (metadata == null || metadata.getHttpPushType() == null) {
            return false;
        }
        PushTypeEnum pushType = PushTypeEnum.getPushTypeEnum(metadata.getHttpPushType());
        return pushType == PushTypeEnum.BROADCAST_SERVER_NOTIFY;
    }

    private record RejectCheck(String reason, ReactiveValidator<Packet> shouldReject) {
    }
}
