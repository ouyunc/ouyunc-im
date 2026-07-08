package com.ouyunc.mq.rocket;

import com.ouyunc.mq.core.MqHeaderKeys;
import com.ouyunc.mq.core.api.MqPublisher;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * RocketMQ {@link MqPublisher} 实现。
 */
public class RocketMqPublisher implements MqPublisher {

    private final RocketMQTemplate rocketMQTemplate = RocketFactory.ROCKET_TEMPLATE.instance();

    @Override
    public CompletableFuture<?> send(String topic, String payload) {
        return send(topic, null, payload);
    }

    @Override
    public CompletableFuture<?> send(String topic, String key, String payload) {
        return send(topic, key, payload, null);
    }

    @Override
    public CompletableFuture<?> send(String topic, String key, String payload, Map<String, Object> headers) {
        MessageBuilder<String> builder = MessageBuilder.withPayload(payload);
        if (headers != null) {
            headers.forEach((name, value) -> mapHeader(builder, name, value));
        }
        if (StringUtils.isNotBlank(key)) {
            builder.setHeader(MessageConst.PROPERTY_KEYS, key);
        }
        org.springframework.messaging.Message<String> message = builder.build();
        CompletableFuture<SendResult> future = new CompletableFuture<>();
        rocketMQTemplate.asyncSend(topic, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                future.complete(sendResult);
            }

            @Override
            public void onException(Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private static void mapHeader(MessageBuilder<String> builder, String name, Object value) {
        if (value == null) {
            return;
        }
        if (MqHeaderKeys.CORRELATION_ID.equals(name)) {
            builder.setHeader(MessageConst.PROPERTY_CORRELATION_ID, String.valueOf(value));
            return;
        }
        if (MqHeaderKeys.MESSAGE_KEY.equals(name)) {
            builder.setHeader(MessageConst.PROPERTY_KEYS, String.valueOf(value));
            return;
        }
        builder.setHeader(name, value);
    }
}
