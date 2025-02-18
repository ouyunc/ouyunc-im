package com.ouyunc.message.listener;

import com.ouyunc.base.constant.JdbcSqlConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.core.listener.event.SaveMessageEvent;
import com.ouyunc.db.jdbc.JdbcFactory;
import com.ouyunc.db.mongo.MongodbFactory;
import com.ouyunc.domain.entity.MessageEntity;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.mq.kafka.KafkaFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * @Author fzx
 * @Description: 撤销消息监听器
 **/
public class WithdrawMessageListener implements MessageListener<SaveMessageEvent> {
    private static final Logger log = LoggerFactory.getLogger(WithdrawMessageListener.class);

    /**
     * kafkaTemplate
     */
    private static final KafkaTemplate<String, Object> kafkaTemplate = KafkaFactory.KAFKA_TEMPLATE.instance();

    /**
     * mongoTemplate
     */
    private static final MongoTemplate mongoTemplate = MongodbFactory.MONGODB_TEMPLATE.instance();

    /**
     * jdbcTemplate
     */
    private static final JdbcTemplate jdbcTemplate = JdbcFactory.JDBC_TEMPLATE.instance();

    /**
     * @Author fzx
     * @Description  撤销消息监听器， 这里的逻辑需要和SaveMessageListener 的逻辑保持呼应
     * @Param [event]
     * @Return void
     */
    @Override
    public void onApplicationEvent(SaveMessageEvent event) {
        if (event.getSource() instanceof Packet packet) {
            log.debug("撤销消息: {} 从数据库和mongodb 中", packet);
            // 在事务中执行
            Boolean executeResult = JdbcFactory.JDBC_TEMPLATE.withTransaction().execute(status -> {
                try {
                    // 数据库中是永久保存的
                    jdbcTemplate.update(JdbcSqlConstant.MYSQL.WITHDRAW_MESSAGE.sql(), NumberConstant.NUMBER_1 ,packet.getPacketId());
                    // 保存到mongodb 默认时效三个月，可根据配置文件配置
                    // 创建查询条件
                    Query query = new Query(Criteria.where(MessageEntity.Fields.id).is(packet.getPacketId()));
                    // 创建更新操作
                    Update update = new Update().set(MessageEntity.Fields.withdrawn, NumberConstant.NUMBER_1);
                    // 执行更新操作
                    mongoTemplate.updateFirst(query, update, MessageEntity.class);
                } catch (Exception e) {
                    log.error("撤销消息从数据库和mongodb 中异常: {}", e.getMessage());
                    // 发送消息到异常事件中 @todo 定义异常类型
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.PERSISTENCE_ERROR, "撤销消息从数据库和mongodb 中异常", packet), true);
                    status.setRollbackOnly();
                    return false;
                }
                return true;
            });
            if (Boolean.FALSE.equals(executeResult)) {
                log.error("撤销消息从数据库和mongodb 中事务异常");
            }
        }
    }
}
