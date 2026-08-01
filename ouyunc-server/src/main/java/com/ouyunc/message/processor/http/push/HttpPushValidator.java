package com.ouyunc.message.processor.http.push;

import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.constant.enums.PushChannelEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.model.MessagePushRequest;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.http.HttpContext;
import com.ouyunc.message.http.HttpPipelineException;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;

/**
 * HTTP 推送前置校验：开关、请求参数、JWT、类型白名单、入口鉴权。
 * <p>好友/群成员/客服路由等业务规则由各投递策略自行校验。</p>
 */
public final class HttpPushValidator {

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
        HttpPushJwtAuth.validateResolvedPacketScope(httpContext, request, packet);
        HttpPushSupportedTypes.validate(packet);
        verifyIngress(packet, httpContext);
        return packet;
    }

    private static void validateFeatureEnabled() throws HttpPipelineException {
        if (!MessageServerContext.serverProperties().isHttpPushEnabled()) {
            throw new HttpPipelineException(HttpResponseStatus.NOT_FOUND, HttpResponseCodeEnum.NOT_FOUND,
                    "HTTP 推送未开启");
        }
        if (MessageServerContext.serverProperties().getHttpBusinessExecutorThreads() <= 0) {
            throw new HttpPipelineException(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    HttpResponseCodeEnum.INTERNAL_SERVER_ERROR,
                    "HTTP 推送已开启但 ouyunc.message.http.business-executor-threads<=0，"
                            + "请配置 >0（建议 16）以免阻塞 Netty EventLoop");
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
}
