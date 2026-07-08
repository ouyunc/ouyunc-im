package com.ouyunc.message.listener;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.EventRingEnum;
import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.model.SendResult;
import com.ouyunc.core.listener.MessageEventListener;
import com.ouyunc.core.listener.EventListener;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.mq.core.MqFactory;
import com.ouyunc.mq.core.MqHeaderKeys;
import com.ouyunc.mq.core.api.MqPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author fzx
 * @Description: 消息发送失败监听器， 可以做消息日志的记录，重发等操作
 */
@EventListener(ring = EventRingEnum.SEND_FAIL_MESSAGE)
class SendFailMessageEventListener implements MessageEventListener<MessageEvent> {
    private static final Logger log = LoggerFactory.getLogger(SendFailMessageEventListener.class);

    private static final MqPublisher mqPublisher = MqFactory.PUBLISHER.instance();

    /**
     * @Author fzx
     * @Description 处理发送消息失败的事件
     */
    @Override
    public EventType type() {
        return MessageEventTypeEnum.SEND_FAIL;
    }

    @Override
    public void onEvent(MessageEvent event) {
        if (log.isDebugEnabled()) {
            log.error("消息发送失败事件监听器正在处理：{}", JSON.toJSONString(event.getSource()));
        }
        // 这里可以丢到mq中去处理，注意：发送失败的消息可能是重试的或者集群间消息传递，所以可能业务上需要做幂等处理
        if (event.getSource() instanceof SendResult sendResult) {
            Map<String, Object> headers = new HashMap<>();
            headers.put(MqHeaderKeys.CORRELATION_ID, sendResult.getPacket().getPacketId());
            mqPublisher.send(MqConstant.MQ_MESSAGE_SEND_FAIL_TOPIC, null,
                    JSON.toJSONString(sendResult), headers);
        }
    }
}
