package com.ouyunc.message.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.message.context.MessageServerContext;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 登录路由本地缓存的跨节点失效总线。
 *
 * <p>登录绑定、解绑属于低频控制面操作，使用 Redisson Topic 通知所有 IM 节点删除本地 Caffeine。
 * Topic 不作为正确性存储：发布或订阅失败时，路由缓存仍会在短 TTL 后从 Redis 权威目录刷新。</p>
 */
public final class LoginRouteCacheInvalidationBus {

    private static final Logger log = LoggerFactory.getLogger(LoginRouteCacheInvalidationBus.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static volatile Integer listenerId;

    private LoginRouteCacheInvalidationBus() {
    }

    /**
     * 在 Netty 对外端口打开前启动订阅，避免服务已接收消息但尚未接收缓存失效事件。
     */
    public static synchronized void start() {
        if (!MessageServerContext.serverProperties().isClusterEnable() || listenerId != null) {
            return;
        }
        try {
            RTopic topic = MessageServerContext.redissonClient.getTopic(
                    CacheConstant.LOGIN_ROUTE_CACHE_INVALIDATE_CHANNEL);
            listenerId = topic.addListener(String.class, new MessageListener<String>() {
                @Override
                public void onMessage(CharSequence channel, String message) {
                    apply(message);
                }
            });
            log.info("登录路由本机缓存失效订阅已启动 channel={}",
                    CacheConstant.LOGIN_ROUTE_CACHE_INVALIDATE_CHANNEL);
        } catch (Exception e) {
            log.error("登录路由本机缓存失效订阅失败，将依赖短 TTL 纠偏", e);
        }
    }

    /**
     * 异步发布失效事件，不阻塞登录绑定锁和断连清理线程。
     */
    public static void publish(String appKey, String identity, byte deviceType) {
        if (!MessageServerContext.serverProperties().isClusterEnable()
                || StringUtils.isAnyBlank(appKey, identity)) {
            return;
        }
        try {
            String payload = JSON.writeValueAsString(new InvalidateEvent(appKey, identity, deviceType));
            MessageServerContext.redissonClient
                    .getTopic(CacheConstant.LOGIN_ROUTE_CACHE_INVALIDATE_CHANNEL)
                    .publishAsync(payload)
                    .whenComplete((receivers, error) -> {
                        if (error != null) {
                            log.warn("登录路由缓存失效发布失败 appKey={} identity={}: {}",
                                    appKey, identity, error.getMessage());
                        } else if (log.isDebugEnabled()) {
                            log.debug("登录路由缓存失效已发布 appKey={} identity={} deviceType={} receivers={}",
                                    appKey, identity, deviceType, receivers);
                        }
                    });
        } catch (Exception e) {
            log.warn("登录路由缓存失效序列化或提交失败 appKey={} identity={}: {}",
                    appKey, identity, e.getMessage());
        }
    }

    private static void apply(String payload) {
        if (StringUtils.isBlank(payload)) {
            return;
        }
        try {
            InvalidateEvent event = JSON.readValue(payload, InvalidateEvent.class);
            if (event == null || StringUtils.isAnyBlank(event.appKey(), event.identity())) {
                return;
            }
            ClientHelper.invalidateRouteCacheLocal(event.appKey(), event.identity(), event.deviceType());
        } catch (Exception e) {
            log.warn("登录路由缓存失效消息处理失败 payload={}", payload, e);
        }
    }

    /**
     * 停止订阅；重复调用安全。缓存本身无需显式关闭。
     */
    public static synchronized void stop() {
        Integer id = listenerId;
        listenerId = null;
        if (id == null) {
            return;
        }
        try {
            MessageServerContext.redissonClient
                    .getTopic(CacheConstant.LOGIN_ROUTE_CACHE_INVALIDATE_CHANNEL)
                    .removeListener(id);
        } catch (Exception e) {
            log.warn("停止登录路由缓存失效订阅异常: {}", e.getMessage());
        }
    }

    public record InvalidateEvent(String appKey, String identity, byte deviceType) {
    }
}
