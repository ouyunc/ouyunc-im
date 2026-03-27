package com.ouyunc.message.listener;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.MessageEventListener;
import com.ouyunc.core.listener.EventListener;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.db.mongo.MongodbFactory;
import com.ouyunc.domain.entity.MongoExceptionEntity;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Kafka 不可用时，将异常内容落 Mongo（原 {@code ExceptionDisruptorEventProcessor} 逻辑）。
 */
@EventListener(order = 90)
class ExceptionPersistMessageEventListener implements MessageEventListener<MessageEvent> {

    private final MongoTemplate mongoTemplate = MongodbFactory.MONGODB_TEMPLATE.instance();

    @Override
    public EventType type() {
        return MessageEventTypeEnum.EXCEPTION_PERSIST;
    }

    @Override
    public void onEvent(MessageEvent event) {
        MessageEvent inner = (MessageEvent) event.getSource();
        long ts = event.getTimestamp();
        mongoTemplate.insert(new MongoExceptionEntity(MessageContext.idGenerator().generateId(), JSON.toJSONString(inner), ts));
    }
}
