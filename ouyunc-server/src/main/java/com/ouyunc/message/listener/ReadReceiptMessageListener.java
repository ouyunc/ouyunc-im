package com.ouyunc.message.listener;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.ReadReceiptMessageEvent;
import com.ouyunc.mq.kafka.KafkaFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author fzx
 * @Description: 读已回执消息监听器
 **/
public class ReadReceiptMessageListener implements MessageListener<ReadReceiptMessageEvent> {
    private static final Logger log = LoggerFactory.getLogger(ReadReceiptMessageListener.class);


    /**
     * kafkaTemplate
     */
    private static final KafkaTemplate<String, Object> kafkaTemplate = KafkaFactory.KAFKA_TEMPLATE.instance();

//    /**
//     * mongoTemplate
//     */
//    private static final MongoTemplate mongoTemplate = MongodbFactory.MONGODB_TEMPLATE.instance();
//
//    /**
//     * jdbcTemplate
//     */
//    private static final JdbcTemplate jdbcTemplate = JdbcFactory.JDBC_TEMPLATE.instance();


    /**
     * @Author fzx
     * @Description  读已回执消息监听器， 这里的逻辑需要和SaveMessageListener 的逻辑保持呼应,这里可以使用发送mq 来代替，提高服务性能，mq 消费者的执行逻辑就是下面的逻辑，或者使用其他持久化存储来替换
     * log.debug("读已回执消息: {} 从数据库和mongodb 中正在处理", packet);
     *             // 在事务中执行
     *             Message message = packet.getMessage();
     *             // 获取需要撤销的消息id，（这里使用String类型接收）
     *             List<Long> readPacketIds = JSON.parseArray(message.getContent(), Long.class);
     *             if (CollectionUtils.isEmpty(readPacketIds)) {
     *                 log.error("已读回执消息异常,已读回执消息的消息id为空，packet: {}", packet);
     *                 return;
     *             }
     *             // 在事务中执行
     *             Boolean executeResult = JdbcFactory.JDBC_TEMPLATE.withTransaction().execute(status -> {
     *                 try {
     *                     // 数据库中是永久保存的
     *                     List<Object[]> batchUpdateReadReceiptArgs = Lists.newArrayList();
     *                     List<Object[]> batchInsertReadReceiptArgs = Lists.newArrayList();
     *                     // 创建 BulkOperations 对象，使用 ORDERED 模式，按顺序执行操作
     *                     BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.ORDERED, MessageEntity.class);
     *                     for (Long readPacketId : readPacketIds) {
     *                         batchUpdateReadReceiptArgs.add(new Object[]{NumberConstant.NUMBER_1, readPacketId});
     *                         batchInsertReadReceiptArgs.add(new Object[]{MessageServerContext.<Long>idGenerator().generateId(), readPacketId, message.getFrom(), message.getMetadata().getServerTime()});
     *                         // 创建查询条件，根据 ID 查找文档
     *                         Query query = new Query(Criteria.where(MessageEntity.Fields.id).is(readPacketId));
     *                         // 创建更新操作
     *                         Update update = new Update().set(MessageEntity.Fields.read, NumberConstant.NUMBER_1);
     *                         // 将更新操作添加到 BulkOperations 中
     *                         bulkOps.updateOne(query, update);
     *                     }
     *                     // 插入已读回执记录
     *                     jdbcTemplate.batchUpdate(JdbcSqlConstant.MYSQL.INSERT_READ_RECEIPT_MESSAGE.sql(), batchInsertReadReceiptArgs);
     *                     jdbcTemplate.batchUpdate(JdbcSqlConstant.MYSQL.UPDATE_READ_MESSAGE.sql(), batchUpdateReadReceiptArgs);
     *                     // 执行mongodb批量更新操作
     *                     bulkOps.execute();
     *                 } catch (Exception e) {
     *                     log.error("读已回执消息从数据库和mongodb 中修改异常: {}", e.getMessage());
     *                     // 发送消息到异常事件中
     *                     MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.PERSISTENCE_ERROR, "读已回执消息从数据库和mongodb 中修改异常", packet), true);
     *                     status.setRollbackOnly();
     *                     return false;
     *                 }
     *                 return true;
     *             });
     *             if (Boolean.FALSE.equals(executeResult)) {
     *                 log.error("读已回执消息从数据库和mongodb 中事务异常");
     *             }
     * @Param [event]
     * @Return void
     */
    @Override
    public void onApplicationEvent(ReadReceiptMessageEvent event) {
        if (event.getSource() instanceof Packet packet) {
            log.debug("发送读已回执消息: {} 到mq中处理", packet);
            Map<String, Object> headers = new HashMap<>();
            headers.put(MessageHeaders.ID, packet.getPacketId());
            headers.put(KafkaHeaders.TOPIC, MqConstant.KAFKA_READ_RECEIPT_MESSAGE_TOPIC);
            kafkaTemplate.send(MessageBuilder.withPayload(JSON.toJSONString(packet)).copyHeadersIfAbsent(headers).build());
        }
    }
}
