package com.ouyunc.message.listener;

import com.ouyunc.base.constant.enums.EventRingEnum;
import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.EventListener;
import com.ouyunc.core.listener.MessageEventListener;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.properties.MessageProperties;
import com.ouyunc.db.mongo.MongodbFactory;
import com.ouyunc.domain.entity.MongoExceptionEntity;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * MQ 不可用时，将 ExceptionRecord JSON 落 Mongo；失败只记日志，禁止再发 EXCEPTION。
 */
@EventListener(ring = EventRingEnum.SLOW)
class ExceptionPersistMessageEventListener implements MessageEventListener<MessageEvent> {
    private static final Logger log = LoggerFactory.getLogger(ExceptionPersistMessageEventListener.class);

    @Override
    public EventType type() {
        return MessageEventTypeEnum.EXCEPTION_PERSIST;
    }

    @Override
    public void onEvent(MessageEvent event) {
        MessageProperties properties = MessageContext.messageProperties;
        if (properties != null && !properties.isExceptionPersistEnabled()) {
            log.warn("异常 Persist 已关闭，跳过落库");
            return;
        }
        Object raw = event.getSource();
        if (!(raw instanceof String json) || StringUtils.isBlank(json)) {
            log.error("EXCEPTION_PERSIST source 非法，已丢弃: {}", raw == null ? null : raw.getClass().getName());
            return;
        }
        try {
            MongoTemplate mongoTemplate = MongodbFactory.MONGODB_TEMPLATE.instance();
            if (mongoTemplate == null) {
                log.error("MongoTemplate 不可用，异常记录未落库");
                return;
            }
            long ts = event.getTimestamp();
            mongoTemplate.insert(new MongoExceptionEntity(MessageContext.idGenerator().generateId(), json, ts));
        } catch (Exception ex) {
            log.error("异常 Persist 落 Mongo 失败", ex);
        }
    }
}
