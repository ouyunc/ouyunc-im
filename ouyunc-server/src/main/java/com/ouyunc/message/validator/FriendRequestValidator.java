package com.ouyunc.message.validator;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.db.mongo.MongodbFactory;
import com.ouyunc.domain.constants.FriendRequestStatus;
import com.ouyunc.domain.entity.MongoFriendRequestSessionEntity;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Date;

/**
 * @author fzx
 * @description 是否存在好友请求校验
 */
public enum FriendRequestValidator implements Validator<Packet> {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(FriendRequestValidator.class);

    private static final MongoTemplate mongoTemplate = MongodbFactory.MONGODB_TEMPLATE.instance();

    /***
     * @author fzx
     * @description 校验是否存在有效的好友请求，如果存在返回true， 否则返回false
     */
    @Override
    public boolean verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        // 判断是存在有效的好友请求记录，如果不存在则
        Criteria criteria = Criteria.where(MongoFriendRequestSessionEntity.Fields.to).is(message.getFrom())
                .and(MongoFriendRequestSessionEntity.Fields.from).is(message.getTo())
                .and(MongoFriendRequestSessionEntity.Fields.status).is(FriendRequestStatus.PENDING.value())
                .and(MongoFriendRequestSessionEntity.Fields.expireAt).gt(new Date());
        Query query = new Query(criteria);
        return mongoTemplate.findOne(query, MongoFriendRequestSessionEntity.class) != null;
    }
}
