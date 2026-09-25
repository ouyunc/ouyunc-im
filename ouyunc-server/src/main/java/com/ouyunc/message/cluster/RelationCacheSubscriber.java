package com.ouyunc.message.cluster;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.model.RelationCacheInvalidateEvent;
import com.ouyunc.message.context.MessageServerContext;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 订阅 Redis 关系本机缓存失效频道（Redisson Topic）。
 * <p>payload 为 {@link RelationCacheInvalidateEvent} JSON。订阅失败不阻断启动，依赖布尔/实体短 TTL 纠偏。</p>
 */
public final class RelationCacheSubscriber {

    private static final Logger log = LoggerFactory.getLogger(RelationCacheSubscriber.class);

    private static volatile Integer listenerId;

    private RelationCacheSubscriber() {
    }

    /**
     * 幂等启动订阅；重复调用直接返回。
     */
    public static synchronized void start() {
        if (listenerId != null) {
            return;
        }
        try {
            RTopic topic = MessageServerContext.redissonClient.getTopic(
                    CacheConstant.RELATION_CACHE_INVALIDATE_CHANNEL);
            listenerId = topic.addListener(String.class, new MessageListener<String>() {
                @Override
                public void onMessage(CharSequence channel, String msg) {
                    if (StringUtils.isBlank(msg)) {
                        return;
                    }
                    try {
                        RelationCacheInvalidateEvent event = JSON.parseObject(msg, RelationCacheInvalidateEvent.class);
                        RelationCacheInvalidateSupport.applyLocal(event);
                    } catch (Exception e) {
                        log.warn("关系本机缓存失效消息处理失败 payload={}", msg, e);
                    }
                }
            });
            log.info("关系本机缓存失效订阅已启动 channel={}", CacheConstant.RELATION_CACHE_INVALIDATE_CHANNEL);
        } catch (Exception e) {
            log.error("关系本机缓存失效订阅失败，将仅依赖本地缓存过期纠偏", e);
        }
    }

    /**
     * 停止订阅；在服务停机时调用。
     */
    public static synchronized void stop() {
        Integer id = listenerId;
        listenerId = null;
        if (id == null) {
            return;
        }
        try {
            MessageServerContext.redissonClient.getTopic(CacheConstant.RELATION_CACHE_INVALIDATE_CHANNEL)
                    .removeListener(id);
        } catch (Exception e) {
            log.warn("停止关系本机缓存订阅异常: {}", e.getMessage());
        }
    }
}
