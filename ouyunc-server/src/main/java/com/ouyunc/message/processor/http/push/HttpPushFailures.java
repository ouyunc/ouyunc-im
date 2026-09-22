package com.ouyunc.message.processor.http.push;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.exception.ExceptionReporter;
import com.ouyunc.message.http.HttpPipelineException;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;

/**
 * HTTP 推送失败构造：统一发事件并抛 {@link HttpPipelineException}。
 */
public final class HttpPushFailures {

    private static final String SCENE = "HttpPushFailures.publish";

    private HttpPushFailures() {
    }

    /** 业务拒绝：403。 */
    public static HttpPipelineException forbidden(Packet packet, String message) {
        return forbidden(packet, ExceptionCodeEnum.HTTP_PUSH_BUSINESS_REJECT, message);
    }

    public static HttpPipelineException forbidden(Packet packet, ExceptionCodeEnum code, String message) {
        String reason = StringUtils.defaultIfBlank(message,
                code != null ? code.getMessage() : "HTTP 推送业务校验未通过");
        ExceptionCodeEnum eventCode = code != null ? code : ExceptionCodeEnum.HTTP_PUSH_BUSINESS_REJECT;
        ExceptionReporter.reportBusiness(eventCode, reason, SCENE, packet);
        return new HttpPipelineException(HttpResponseStatus.FORBIDDEN, HttpResponseCodeEnum.FORBIDDEN, reason);
    }

    /** 服务端异常：500。 */
    public static HttpPipelineException serverError(Packet packet, String message) {
        String reason = StringUtils.defaultIfBlank(message, "HTTP 推送处理异常");
        ExceptionReporter.reportSystem(ExceptionCodeEnum.UNKNOWN_ERROR, reason, SCENE, packet);
        return new HttpPipelineException(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                HttpResponseCodeEnum.INTERNAL_SERVER_ERROR, reason);
    }

    public static String formatError(Throwable error) {
        if (error == null) {
            return "HTTP 推送校验异常";
        }
        String message = error.getMessage();
        if (StringUtils.isNotBlank(message)) {
            return message;
        }
        return error.getClass().getSimpleName();
    }
}
