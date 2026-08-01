package com.ouyunc.message.processor.http.push;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.processor.http.push.delivery.HttpProcessor;
import com.ouyunc.message.processor.http.push.delivery.HttpPushDeliverySupport;
import com.ouyunc.message.processor.http.push.delivery.HttpPushProcessorStrategies;
import com.ouyunc.repository.DefaultRepository;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP 推送专用投递入口：按消息类型选择策略 {@code preProcess} + {@code process}。
 */
public final class HttpPushProcessorDelegate {

    private static final Logger log = LoggerFactory.getLogger(HttpPushProcessorDelegate.class);

    private HttpPushProcessorDelegate() {
    }

    /**
     * 幂等占位后、ACCEPTED 前调用：由对应策略 {@link HttpProcessor#preProcess}，失败抛 HTTP 403/500。
     */
    public static void preProcessOrThrow(Packet packet) throws HttpPipelineException {
        HttpProcessor strategy = requireStrategy(packet);
        try {
            strategy.preProcess(packet);
        } catch (RuntimeException ex) {
            log.error("HTTP 推送 preProcess 异常: {}", ex.getMessage(), ex);
            throw HttpPushFailures.serverError(packet, HttpPushFailures.formatError(ex));
        }
    }

    /**
     * 异步投递（fire-and-forget）：归档后调用策略 {@link HttpProcessor#process}。
     */
    public static void delegate(Packet packet) {
        if (packet == null || packet.getMessage() == null) {
            return;
        }
        DefaultRepository.INSTANCE.publishArchiveAsync(packet);
        HttpProcessor strategy = HttpPushProcessorStrategies.get(packet.getMessageType());
        if (strategy == null) {
            log.error("HTTP 推送投递不支持 messageType={}", packet.getMessageType());
            HttpPushDeliverySupport.publishException(ExceptionCodeEnum.ILLEGAL_MESSAGE_TYPE_ERROR,
                    "HTTP 推送不支持的消息类型", packet);
            return;
        }
        try {
            strategy.process(packet);
        } catch (Exception ex) {
            log.error("HTTP 推送 process 异常, messageId={}", packet.getMessage().getId(), ex);
            HttpPushDeliverySupport.publishException(ExceptionCodeEnum.UNKNOWN_ERROR, ex.getMessage(), packet);
        }
    }

    private static HttpProcessor requireStrategy(Packet packet) throws HttpPipelineException {
        if (packet == null || packet.getMessage() == null) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "HTTP 推送 Packet 不能为空");
        }
        HttpProcessor strategy = HttpPushProcessorStrategies.get(packet.getMessageType());
        if (strategy == null) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "HTTP 推送不支持的消息类型 messageType=" + packet.getMessageType());
        }
        return strategy;
    }
}
