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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

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
        try {
            redisTemplate.expire(loginCacheKey, Duration.ofSeconds(loginExpireTime));
            redisTemplate.opsForZSet().add(appKeyConnectionsCacheKey, comboIdentity, TimeUtil.currentTimeMillis() + loginExpireTime * MessageConstant.NUMBER_1000);
        } catch (Exception e) {
            log.error("客户端登录保活刷新失败, identity={}, appKey={}, reason={}", loginClientInfo.getIdentity(), loginClientInfo.getAppKey(), e.getMessage());
        }
    }
}
