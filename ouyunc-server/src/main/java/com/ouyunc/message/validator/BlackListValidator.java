package com.ouyunc.message.validator;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.JdbcSqlConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.db.jdbc.JdbcFactory;
import com.ouyunc.domain.constants.IdentityType;
import com.ouyunc.domain.entity.BlacklistEntity;
import com.ouyunc.repository.DefaultRepository;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * @author fzx
 * @description 黑名单校验器
 */
public enum BlackListValidator implements Validator<Packet> {

    INSTANCE;
    private static final Logger log = LoggerFactory.getLogger(BlackListValidator.class);

    /**
     * jdbcClient
     */
    private static final JdbcClient jdbcClient = JdbcFactory.JDBC_CLIENT.instance();

    /**
     * redisTemplate
     */
    private static final RedisTemplate<String, ?> redisTemplate = CacheFactory.REDIS.instance();

   /***
     * @author fzx
     * @description 校验是否在黑名单, 在黑名单 返回true， 不在黑名单，返回false
     */
    @Override
    public boolean verify(Packet packet, ChannelHandlerContext ctx) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        Metadata metadata = message.getMetadata();
        Long joinTimestamp = redisTemplate.<String, Long>opsForHash().get(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.BLACKLIST + message.getTo(), message.getFrom());
        if (joinTimestamp != null && joinTimestamp > 0) {
            return true;
        }
        // 从数据库查询
        try {
            BlacklistEntity blacklistEntity = jdbcClient.sql(JdbcSqlConstant.MYSQL.SELECT_BLACKLIST.sql())
                    .params(to, from, IdentityType.ONE_2_ONE.value())
                    .query(BlacklistEntity.class)
                    .single();
            return true;
        }catch (EmptyResultDataAccessException e) {
            return false;
        }catch (IncorrectResultSizeDataAccessException e) {
            log.error("从db查询黑名单异常,存在多个黑名单数据，请知悉: {}", e.getMessage());
            return true;
        } catch (Exception e) {
            log.error("从db查询黑名单异常: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
