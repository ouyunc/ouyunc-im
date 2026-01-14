package com.ouyunc.message.disruptor;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.core.processor.Processor;
import com.ouyunc.db.mongo.MongodbFactory;
import com.ouyunc.domain.entity.MongoExceptionEntity;
import com.ouyunc.message.context.MessageServerContext;
import org.springframework.data.mongodb.core.MongoTemplate;


/**
 * 异常消息队列处理器
 */
public class ExceptionDisruptorEventProcessor implements Processor<ExceptionEvent, Long> {

    MongoTemplate mongoTemplate = MongodbFactory.MONGODB_TEMPLATE.instance();


    /**
     * 处理异常消息
     * @param exception
     * @param timestamp
     */
    @Override
    public void process(ExceptionEvent exception, Long timestamp) {
        // 直接保存到mongo 中
        mongoTemplate.insert(new MongoExceptionEntity(MessageServerContext.idGenerator().generateId(), JSON.toJSONString(exception), timestamp));
    }
}
