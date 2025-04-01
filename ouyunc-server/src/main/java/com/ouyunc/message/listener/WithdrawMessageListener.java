package com.ouyunc.message.listener;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.WithdrawMessageEvent;
import com.ouyunc.mq.kafka.KafkaFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author fzx
 * @Description: 撤销消息监听器
 **/
public class WithdrawMessageListener implements MessageListener<WithdrawMessageEvent> {
    private static final Logger log = LoggerFactory.getLogger(WithdrawMessageListener.class);

    /**
     * kafkaTemplate
     */
    private static final KafkaTemplate<String, Object> kafkaTemplate = KafkaFactory.KAFKA_TEMPLATE.instance();


    /**
     * @Author fzx
     * @Description  撤销消息监听器， 这里的逻辑需要和SaveMessageListener 的逻辑保持呼应， 下面可以使用mq 来替换，减轻服务压力，提高效率
     * log.debug("撤销消息: {} 从数据库和mongodb 中", packet);
     *             Message message = packet.getMessage();
     *             // 获取需要撤销的消息id，（这里使用String类型接收）
     *             List<Long> withdrawPacketIds = JSON.parseArray(message.getContent(), Long.class);
     *             if (CollectionUtils.isEmpty(withdrawPacketIds)) {
     *                 log.error("撤销消息异常,撤销消息的消息id为空，packet: {}", packet);
     *                 return;
     *             }
     *             // 在事务中执行
     *             Boolean executeResult = JdbcFactory.JDBC_TEMPLATE.withTransaction().execute(status -> {
     *                 try {
     *                     // 数据库中是永久保存的
     *                     List<Object[]> batchWithdrawArgs = Lists.newArrayList();
     *                     // 创建 BulkOperations 对象，使用 ORDERED 模式，按顺序执行操作
     *                     BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.ORDERED, MessageEntity.class);
     *                     for (Long withdrawPacketId : withdrawPacketIds) {
     *                         batchWithdrawArgs.add(new Object[]{NumberConstant.NUMBER_1, withdrawPacketId});
     *                         // 创建查询条件，根据 ID 查找文档
     *                         Query query = new Query(Criteria.where(MessageEntity.Fields.id).is(withdrawPacketId));
     *                         // 创建更新操作
     *                         Update update = new Update().set(MessageEntity.Fields.withdrawn, NumberConstant.NUMBER_1);
     *                         // 将更新操作添加到 BulkOperations 中
     *                         bulkOps.updateOne(query, update);
     *                     }
     *                     jdbcTemplate.batchUpdate(JdbcSqlConstant.MYSQL.UPDATE_WITHDRAW_MESSAGE.sql(), batchWithdrawArgs);
     *                     // 执行mongodb批量更新操作
     *                     bulkOps.execute();
     *                 } catch (Exception e) {
     *                     log.error("撤销消息从数据库和mongodb 中异常: {}", e.getMessage());
     *                     // 发送消息到异常事件中 @todo 定义异常类型
     *                     MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.PERSISTENCE_ERROR, "撤销消息从数据库和mongodb 中异常", packet), true);
     *                     status.setRollbackOnly();
     *                     return false;
     *                 }
     *                 return true;
     *             });
     *             if (Boolean.FALSE.equals(executeResult)) {
     *                 log.error("撤销消息从数据库和mongodb 中事务异常");
     *             }
     * @Param [event]
     * @Return void
     */
    @Override
    public void onApplicationEvent(WithdrawMessageEvent event) {
        if (event.getSource() instanceof Packet packet) {
            log.debug("发送撤销消息: {} 到mq中处理", packet);
            Map<String, Object> headers = new HashMap<>();
            headers.put(KafkaHeaders.CORRELATION_ID, packet.getPacketId());
            headers.put(KafkaHeaders.TOPIC, MqConstant.KAFKA_WITHDRAW_MESSAGE_TOPIC);
            kafkaTemplate.send(MessageBuilder.withPayload(JSON.toJSONString(packet)).copyHeadersIfAbsent(headers).build());
        }
    }
}
