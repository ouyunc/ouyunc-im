package com.ouyunc.repository.support;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

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
