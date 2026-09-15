package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.JdbcSqlDialectHolder;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.domain.entity.MongoUserEntity;
import com.ouyunc.domain.entity.UserEntity;
import com.ouyunc.core.context.MessageContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.concurrent.TimeUnit;

/**
 * 用户实体多级缓存查询。
 */
public final class UserRepositorySupport {

    private static final Logger log = LoggerFactory.getLogger(UserRepositorySupport.class);

    private final RepositoryInfrastructure infra;

    public UserRepositorySupport(RepositoryInfrastructure infra) {
        this.infra = infra;
    }

    @SuppressWarnings("unchecked")
    public UserEntity getUserEntity(String appKey, String identity) {
        String userCacheKey = CacheConstant.buildUserCacheKey(appKey, identity);

        // 1. 本地缓存
        UserEntity userEntity = MessageContext.userEntityCache.get(userCacheKey);
        if (userEntity != null) {
            return userEntity;
        }

        // 2. Redis缓存
        userEntity = (UserEntity) infra.redisTemplate.opsForValue().get(userCacheKey);
        if (userEntity != null) {
            // L2 命中只填本地，禁止回写 Redis 续期
            fillLocalUserCache(userCacheKey, userEntity);
            return userEntity;
        }

        // 3. MongoDB
        try {
            MongoUserEntity mongoUser = infra.mongoTemplate.findOne(
                    Query.query(Criteria.where(UserEntity.Fields.id).is(Long.parseLong(identity))
                            .and(UserEntity.Fields.appKey).is(appKey)
                            .and(UserEntity.Fields.delFlag).is(0L)),
                    MongoUserEntity.class);
            if (mongoUser != null) {
                userEntity = convertMongoUserToUser(mongoUser);
                if (userEntity != null && appKey.equals(userEntity.getAppKey())) {
                    updateUserCache(userCacheKey, userEntity);
                    return userEntity;
                }
            }
        } catch (Exception e) {
            log.warn("从MongoDB查询用户异常, appKey: {}, identity: {}", appKey, identity, e);
        }

        // 4. MySQL
        try {
            userEntity = infra.jdbcClient.sql(JdbcSqlDialectHolder.selectUser())
                    .param(UserEntity.Fields.id, identity)
                    .param(UserEntity.Fields.appKey, appKey)
                    .query(UserEntity.class)
                    .single();
            if (userEntity != null && !appKey.equals(userEntity.getAppKey())) {
                log.warn("用户租户不匹配, identity={}, expectAppKey={}, actual={}",
                        identity, appKey, userEntity.getAppKey());
                return null;
            }
            // 存到缓存中,30天
            updateUserCache(userCacheKey, userEntity);
            return userEntity;
        } catch (EmptyResultDataAccessException e) {
            log.warn("用户不存在, identity: {}", identity);
            return null;
        } catch (IncorrectResultSizeDataAccessException e) {
            log.error("同一个identity存在多个用户, identity: {}", identity);
            throw new RuntimeException("同一个identity存在多个用于, identity: " + identity);
        } catch (Exception e) {
            log.error("获取用户实体异常, identity: {}, 原因：{}", identity, e.getMessage());
            throw new RuntimeException("获取用户实体异常, identity: " + identity);
        }
    }

    void updateUserCache(String cacheKey, UserEntity userEntity) {
        if (userEntity != null) {
            fillLocalUserCache(cacheKey, userEntity);
            infra.redisTemplate.opsForValue().set(cacheKey, userEntity, NumberConstant.NUMBER_30 * MessageConstant.DAY_TIMESTAMP, TimeUnit.MILLISECONDS);
        }
    }

    /** 仅填本地缓存（Redis 命中路径） */
    private void fillLocalUserCache(String cacheKey, UserEntity userEntity) {
        if (userEntity != null) {
            MessageContext.userEntityCache.put(cacheKey, userEntity);
        }
    }

    UserEntity convertMongoUserToUser(MongoUserEntity mongoUser) {
        if (mongoUser == null) {
            return null;
        }
        UserEntity userEntity = new UserEntity();
        userEntity.setId(mongoUser.getId());
        userEntity.setOpenId(mongoUser.getOpenId());
        userEntity.setCode(mongoUser.getCode());
        userEntity.setUsername(mongoUser.getUsername());
        userEntity.setPassword(mongoUser.getPassword());
        userEntity.setNickName(mongoUser.getNickName());
        userEntity.setAvatar(mongoUser.getAvatar());
        userEntity.setMotto(mongoUser.getMotto());
        userEntity.setAge(mongoUser.getAge());
        userEntity.setSex(mongoUser.getSex());
        userEntity.setEmail(mongoUser.getEmail());
        userEntity.setPhoneNum(mongoUser.getPhoneNum());
        userEntity.setIdCardNo(mongoUser.getIdCardNo());
        userEntity.setGroupInvitePolicy(mongoUser.getGroupInvitePolicy());
        userEntity.setFriendJoinPolicy(mongoUser.getFriendJoinPolicy());
        userEntity.setStatus(mongoUser.getStatus());
        userEntity.setAppKey(mongoUser.getAppKey());
        userEntity.setType(mongoUser.getType());
        userEntity.setUnionId(mongoUser.getUnionId());
        userEntity.setLanguage(mongoUser.getLanguage());
        userEntity.setAutoTranslateIn(mongoUser.getAutoTranslateIn());
        userEntity.setCreateTime(mongoUser.getCreateTime());
        userEntity.setUpdateTime(mongoUser.getUpdateTime());
        userEntity.setDelFlag(mongoUser.getDelFlag());
        return userEntity;
    }
}
