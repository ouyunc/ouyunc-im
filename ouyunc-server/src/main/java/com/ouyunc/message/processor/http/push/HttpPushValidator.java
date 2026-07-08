package com.ouyunc.message.processor.http.push;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.PushChannelEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.base.model.MessagePushRequest;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.http.HttpContext;
import com.ouyunc.message.http.HttpPipelineException;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * HTTP 推送前置校验：请求参数、JWT、类型白名单、入口鉴权、业务规则，全部通过后返回可投递的 {@link Packet}。
 */
public final class HttpPushValidator {

    private static final Logger log = LoggerFactory.getLogger(HttpPushValidator.class);

    private HttpPushValidator() {
    }

    /**
     * 统一前置校验并构建 Packet；任一步失败即抛出 {@link HttpPipelineException}。
     */
    public static Packet validateAndPrepare(MessagePushRequest request, HttpContext httpContext)
            throws HttpPipelineException {
        validateFeatureEnabled();
        validatePushChannel(request);
        validateRequest(request, httpContext);
        HttpPushJwtAuth.authenticate(httpContext, request);
        Packet packet = MessagePushPacketConverter.convert(request, httpContext);
        HttpPushSupportedTypes.validate(packet);
        verifyIngress(packet, httpContext);
        verifyBusinessRules(packet);
        return packet;
    }

    private static void validateFeatureEnabled() throws HttpPipelineException {
        if (!MessageServerContext.serverProperties().isHttpPushEnabled()) {
            throw new HttpPipelineException(HttpResponseStatus.NOT_FOUND, HttpResponseCodeEnum.NOT_FOUND,
                    "HTTP 推送未开启");
        }
    }

    private static void validatePushChannel(MessagePushRequest request) throws HttpPipelineException {
        PushChannelEnum channel = PushChannelEnum.getPushChannelEnum(request.getPushChannel());
        if (channel == null) {
            channel = PushChannelEnum.IM;
        }
        if (channel != PushChannelEnum.IM) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "暂不支持 pushChannel=" + request.getPushChannel());
        }
    }

    private static void validateRequest(MessagePushRequest request, HttpContext httpContext)
            throws HttpPipelineException {
        if (request == null) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "请求体不能为空");
        }
        if (StringUtils.isBlank(httpContext.getAppKey())) {
            throw new HttpPipelineException(HttpResponseStatus.UNAUTHORIZED, HttpResponseCodeEnum.UNAUTHORIZED,
                    "缺少 appKey");
        }
        if (StringUtils.isBlank(request.getMessageId())) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "messageId 不能为空");
        }
        if (StringUtils.isBlank(request.getContent())) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "content 不能为空");
        }
    }

    private static void verifyIngress(Packet packet, HttpContext httpContext) throws HttpPipelineException {
        if (!IngressAuthSupport.INSTANCE.verify(packet, httpContext)) {
            throw new HttpPipelineException(HttpResponseStatus.UNAUTHORIZED, HttpResponseCodeEnum.UNAUTHORIZED,
                    "HTTP 推送鉴权失败");
        }
    }

    private static void verifyBusinessRules(Packet packet) throws HttpPipelineException {
        HttpPushVerifyResult verifyResult = HttpPushValidatorChain.verify(packet, null)
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(error -> {
                    log.error("HTTP 推送校验超时或异常: {}", error.getMessage(), error);
                    return Mono.just(HttpPushVerifyResult.error(HttpPushValidatorChain.formatError(error)));
                })
                .block();
        if (verifyResult == null || verifyResult.isError()) {
            String message = resolveVerifyMessage(verifyResult, "HTTP 推送校验异常");
            log.error("HTTP 推送校验失败, messageId={}, reason={}", packet.getMessage().getId(), message);
            MessageServerContext.publishEvent(new MessageEvent(
                    ExceptionEventPayload.of(ExceptionCodeEnum.UNKNOWN_ERROR, message, packet),
                    MessageEventTypeEnum.EXCEPTION), true);
            throw new HttpPipelineException(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    HttpResponseCodeEnum.INTERNAL_SERVER_ERROR, message);
        }
        if (verifyResult.isReject()) {
            String message = resolveVerifyMessage(verifyResult, "HTTP 推送业务校验未通过");
            log.warn("HTTP 推送业务校验未通过, messageId={}, reason={}, packet={}",
                    packet.getMessage().getId(), message, packet);
            MessageServerContext.publishEvent(new MessageEvent(
                    ExceptionEventPayload.of(ExceptionCodeEnum.UNKNOWN_ERROR, message, packet),
                    MessageEventTypeEnum.EXCEPTION), true);
            throw new HttpPipelineException(HttpResponseStatus.FORBIDDEN, HttpResponseCodeEnum.FORBIDDEN, message);
        }
    }

    private static String resolveVerifyMessage(HttpPushVerifyResult verifyResult, String defaultMessage) {
        if (verifyResult != null && StringUtils.isNotBlank(verifyResult.getMessage())) {
            return verifyResult.getMessage();
        }
        return defaultMessage;
    }
}
