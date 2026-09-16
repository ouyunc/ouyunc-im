package com.ouyunc.core.relation;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.model.RelationCacheInvalidateEvent;
import com.ouyunc.cache.config.CacheFactory;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 关系写完后 PUBLISH，各 IM 节点 {@code RelationCacheInvalidateSubscriber} 清本机 Caffeine。
 * <p>须用 Redisson Topic，与订阅端一致；失败只打日志，不回滚 Redis 关系写入。</p>
 */
public final class RelationCacheInvalidatePublisher {

    private static final Logger log = LoggerFactory.getLogger(RelationCacheInvalidatePublisher.class);

    private RelationCacheInvalidatePublisher() {
    }

    /**
     * 发布失效事件；字段不全或 Redisson 不可用时跳过。
     */
    public static void publish(RelationCacheInvalidateEvent event) {
        if (event == null || StringUtils.isBlank(event.getAppKey()) || StringUtils.isBlank(event.getKind())) {
            return;
        }
        try {
            RedissonClient redisson = CacheFactory.REDISSON.instance();
            if (redisson == null) {
                log.warn("关系本机缓存失效未发布：Redisson 不可用 kind={}", event.getKind());
                return;
            }
            String payload = JSON.toJSONString(event);
            long receivers = redisson.getTopic(CacheConstant.RELATION_CACHE_INVALIDATE_CHANNEL).publish(payload);
            if (log.isDebugEnabled()) {
                log.debug("关系本机缓存失效已发布 kind={} appKey={} receivers={}",
                        event.getKind(), event.getAppKey(), receivers);
            }
        } catch (Exception ex) {
            log.warn("关系本机缓存失效发布失败 kind={}: {}", event.getKind(), ex.getMessage());
        }
    }
}
