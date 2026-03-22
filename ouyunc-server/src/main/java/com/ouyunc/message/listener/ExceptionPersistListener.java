package com.ouyunc.message.listener;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.core.listener.event.ExceptionPersistEvent;
import com.ouyunc.db.mongo.MongodbFactory;
import com.ouyunc.domain.entity.MongoExceptionEntity;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Kafka 不可用时，将异常内容落 Mongo（原 {@code ExceptionDisruptorEventProcessor} 逻辑）。
 */
public class ExceptionPersistListener implements MessageListener<ExceptionPersistEvent> {

    private final MongoTemplate mongoTemplate = MongodbFactory.MONGODB_TEMPLATE.instance();

    @Override
    public void onApplicationEvent(ExceptionPersistEvent event) {
        ExceptionEvent payload = event.getExceptionPayload();
        long ts = event.getTimestamp();
        mongoTemplate.insert(new MongoExceptionEntity(MessageContext.idGenerator().generateId(), JSON.toJSONString(payload), ts));
    }
}
