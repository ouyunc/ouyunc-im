package com.ouyunc.message.processor.http.push;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.processor.http.push.delivery.HttpPushProcessorStrategies;
import com.ouyunc.message.processor.http.push.delivery.HttpProcessor;
import com.ouyunc.message.processor.http.push.delivery.HttpPushDeliverySupport;
import com.ouyunc.repository.DefaultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP 推送专用投递入口：校验通过后归档，再按消息类型选择策略落库并推送。
 */
public final class HttpPushProcessorDelegate {

    private static final Logger log = LoggerFactory.getLogger(HttpPushProcessorDelegate.class);

    private HttpPushProcessorDelegate() {
    }

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
        strategy.process(packet);
    }
}
