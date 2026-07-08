package com.ouyunc.message.processor.http.push;

import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.PushTypeEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.validator.BlackListValidator;
import com.ouyunc.message.validator.FriendShieldValidator;
import com.ouyunc.message.validator.FriendValidator;
import com.ouyunc.message.validator.FromToValidator;
import com.ouyunc.message.validator.GroupInviteSelfValidator;
import com.ouyunc.message.validator.GroupSilenceValidator;
import com.ouyunc.message.validator.GroupUserValidator;
import com.ouyunc.message.validator.PermissionValidator;
import com.ouyunc.message.validator.ReactiveValidator;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP 推送专用校验链：跳过 Channel 登录鉴权；校验时不依赖 {@link io.netty.channel.ChannelHandlerContext}。
 */
public final class HttpPushValidatorChain {

    private static final Logger log = LoggerFactory.getLogger(HttpPushValidatorChain.class);

    private HttpPushValidatorChain() {
    }

    public static Mono<HttpPushVerifyResult> verify(Packet packet, ChannelHandlerContext ctx) {
        List<RejectCheck> checks = buildChecks(packet);
        if (checks.isEmpty()) {
            return Mono.just(HttpPushVerifyResult.pass());
        }
        return Flux.fromIterable(checks)
                .concatMap(check -> check.shouldReject.verify(packet, ctx)
                        .flatMap(reject -> {
                            if (Boolean.TRUE.equals(reject)) {
                                log.warn("HTTP 推送校验未通过: {}, packetId={}, messageId={}",
                                        check.reason, packet.getPacketId(), packet.getMessage().getId());
                                return Mono.just(HttpPushVerifyResult.reject(check.reason));
                            }
                            return Mono.empty();
                        }))
                .next()
                .switchIfEmpty(Mono.just(HttpPushVerifyResult.pass()))
                .onErrorResume(error -> {
                    log.error("HTTP 推送校验异常: {}", error.getMessage(), error);
                    return Mono.just(HttpPushVerifyResult.error(formatError(error)));
                });
    }

    private static List<RejectCheck> buildChecks(Packet packet) {
        boolean broadcast = isBroadcast(packet);
        List<RejectCheck> checks = new ArrayList<>();
        checks.add(new RejectCheck("权限不足，无法发送该类型消息", PermissionValidator.INSTANCE.negate()));
        if (!broadcast) {
            checks.add(new RejectCheck("发送方与接收方不能相同", FromToValidator.INSTANCE));
            checks.add(new RejectCheck("不能邀请自己加入群聊", GroupInviteSelfValidator.INSTANCE));
        }
        return checks;
    }

    static String formatError(Throwable error) {
        if (error == null) {
            return "HTTP 推送校验异常";
        }
        String message = error.getMessage();
        if (StringUtils.isNotBlank(message)) {
            return message;
        }
        return error.getClass().getSimpleName();
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
