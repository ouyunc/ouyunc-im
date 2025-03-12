package com.ouyunc.message.listener;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.SaveMessageEvent;
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
 * @Description: 保存消息监听器
 **/
public class SaveMessageListener implements MessageListener<SaveMessageEvent> {
    private static final Logger log = LoggerFactory.getLogger(SaveMessageListener.class);

    /**
     * kafkaTemplate
     */
    private static final KafkaTemplate<String, Object> kafkaTemplate = KafkaFactory.KAFKA_TEMPLATE.instance();
//
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
     * @Description 保存消息到数据库和mongodb 中；这里有两种方式一种是直接在事件中处理（会影响整体服务性能）2，通过mq发送到队列中，在其他服务来消费处理
     * 这里先使用第一种方式，避免多引入中间件
     *
     * log.debug("保存消息: {} 到数据库和mongodb 中", packet);
     *             // 在事务中执行
     *             Message message = packet.getMessage();
     *             Metadata metadata = message.getMetadata();
     *             Boolean executeResult = JdbcFactory.JDBC_TEMPLATE.withTransaction().execute(status -> {
     *                 try {
     *                     String atJson = message.getAt() == null ? null : JSON.toJSONString(message.getAt());
     *                     jdbcTemplate.update(JdbcSqlConstant.MYSQL.INSERT_MESSAGE.sql(), packet.getPacketId(), packet.getProtocol(), packet.getProtocolVersion(), packet.getDeviceType(), packet.getNetworkType(), packet.getEncryptType(), packet.getSerializeAlgorithm(), packet.getMessageType(), packet.getRetain(), metadata.getClientIp(), message.getFrom(), message.getTo(), message.getContentType(), message.getContent(), message.getExtra(), atJson, message.getQos(), message.getCreateTime(), metadata.getServerTime(), NumberConstant.NUMBER_0, NumberConstant.NUMBER_0);
     *                     // 保存到mongodb 默认时效三个月，可根据配置文件配置
     *                     mongoTemplate.insert(new MessageEntity(packet.getPacketId(), packet.getProtocol(), packet.getProtocolVersion(), packet.getDeviceType(), packet.getNetworkType(), packet.getEncryptType(), packet.getSerializeAlgorithm(), packet.getMessageType(), packet.getRetain(), metadata.getClientIp(), message.getFrom(), message.getTo(), message.getContentType(), message.getContent(), message.getQos(), message.getAt(),message.getExtra(), message.getCreateTime(), metadata.getServerTime(), NumberConstant.NUMBER_0, NumberConstant.NUMBER_0, LocalDateTime.now().plusMonths(NumberConstant.NUMBER_3)));
     *                 } catch (Exception e) {
     *                     log.error("保存消息到数据库和mongodb 中异常: {}", e.getMessage());
     *                     status.setRollbackOnly();
     *                     return false;
     *                 }
     *                 return true;
     *             });
     *             if (Boolean.FALSE.equals(executeResult)) {
     *                 log.error("保存消息到数据库和mongodb 中事务异常");
     *             }
     * @Param [event]
     * @Return void
     */
    @Override
    public void onApplicationEvent(SaveMessageEvent event) {
        if (event.getSource() instanceof Packet packet) {
            log.debug("保存消息: {} 到mq中处理", packet);
            Map<String, Object> headers = new HashMap<>();
            headers.put(KafkaHeaders.CORRELATION_ID, packet.getPacketId());
            headers.put(KafkaHeaders.TOPIC, MqConstant.KAFKA_SAVE_MESSAGE_TOPIC);
            kafkaTemplate.send(MessageBuilder.withPayload(JSON.toJSONString(packet)).copyHeadersIfAbsent(headers).build());
        }
    }
}
