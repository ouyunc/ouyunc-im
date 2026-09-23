package com.ouyunc.repository.support;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 会话 ZSet 索引相关 Redis 操作。
 */
public final class SessionIndexSupport {

    private final StringRedisTemplate stringRedisTemplate;

    public SessionIndexSupport(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 会话/ticket 消息索引 ZSet 成员以 String 序列化写入，删除必须走 {@link StringRedisTemplate}。
     * <p>普通 RedisTemplate 的 value 是 GenericJackson2JsonRedisSerializer，ZREM 字节对不上。</p>
     */
    public Long removeMembers(String zsetKey, Collection<String> members) {
        if (StringUtils.isBlank(zsetKey) || CollectionUtils.isEmpty(members)) {
            return 0L;
        }
        Object[] values = members.toArray();
        Long removed = stringRedisTemplate.opsForZSet().remove(zsetKey, values);
        return removed == null ? 0L : removed;
    }

    /**
     * 管道批量 ZSCORE，一次 RTT；与 members 等长，不存在为 null。
     */
    @SuppressWarnings("unchecked")
    public List<Object> batchZSetScoresPipelined(String zsetKey, List<String> members) {
        if (CollectionUtils.isEmpty(members)) {
            return Collections.emptyList();
        }
        return stringRedisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                ZSetOperations<K, V> zSetOps = operations.opsForZSet();
                for (String member : members) {
                    zSetOps.score((K) zsetKey, (V) member);
                }
                return null;
            }
        });
    }

    public static boolean isZSetScorePresent(Object score) {
        if (score == null) {
            return false;
        }
        if (score instanceof Boolean boolScore) {
            return boolScore;
        }
        return true;
    }

    public static int countPresentZSetScores(List<Object> scores) {
        int count = 0;
        for (Object score : scores) {
            if (isZSetScorePresent(score)) {
                count++;
            }
        }
        return count;
    }
}
