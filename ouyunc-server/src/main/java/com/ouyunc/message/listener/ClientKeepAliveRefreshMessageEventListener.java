package com.ouyunc.message.listener;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.EventRingEnum;
import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.listener.EventListener;
import com.ouyunc.core.listener.MessageEventListener;
import com.ouyunc.core.listener.event.MessageEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;

import java.util.concurrent.TimeUnit;

/**
 * 客户端登录保活刷新监听器（异步事件）。
 */
@EventListener(ring = EventRingEnum.CLIENT_KEEP_ALIVE_REFRESH)
class ClientKeepAliveRefreshMessageEventListener implements MessageEventListener<MessageEvent> {
    private static final Logger log = LoggerFactory.getLogger(ClientKeepAliveRefreshMessageEventListener.class);
    private static final RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();

    @Override
    public EventType type() {
        return MessageEventTypeEnum.CLIENT_KEEP_ALIVE_REFRESH;
    }

    @Override
    public void onEvent(MessageEvent event) {
        if (!(event.getSource() instanceof LoginClientInfo loginClientInfo)) {
            return;
        }
        long loginExpireTime = loginClientInfo.getLoginExpireTime();
        if (loginExpireTime <= 0) {
            return;
        }
        String comboIdentity = IdentityUtil.generalComboIdentity(loginClientInfo.getAppKey(), loginClientInfo.getIdentity(), loginClientInfo.getDeviceType());
        String loginCacheKey = CacheConstant.buildLoginCacheKey(loginClientInfo.getAppKey(), comboIdentity);
        String appKeyConnectionsCacheKey = CacheConstant.buildConnectionsCacheKey(loginClientInfo.getAppKey());
        String presenceKey = CacheConstant.buildLoginPresenceCacheKey(
                loginClientInfo.getAppKey(), loginClientInfo.getIdentity());
        try {
            // 登录 String / conn ZSET / presence SET 均带 {appKey} 哈希标签，集群同 slot，可 pipeline
            redisTemplate.executePipelined(new SessionCallback<>() {
                @SuppressWarnings("unchecked")
                @Override
                public <K, V> Object execute(@NotNull RedisOperations<K, V> operations) throws DataAccessException {
                    operations.expire((K) loginCacheKey, loginExpireTime, TimeUnit.SECONDS);
                    operations.opsForZSet().add((K) appKeyConnectionsCacheKey, (V) comboIdentity,
                            TimeUtil.currentTimeMillis() + loginExpireTime * MessageConstant.NUMBER_1000);
                    operations.expire((K) presenceKey, loginExpireTime, TimeUnit.SECONDS);
                    return null;
                }
            });
        } catch (Exception e) {
            log.error("客户端登录保活刷新失败, identity={}, appKey={}, reason={}", loginClientInfo.getIdentity(), loginClientInfo.getAppKey(), e.getMessage());
        }
    }
}
