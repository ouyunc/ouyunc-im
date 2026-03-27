package com.ouyunc.message.thread;


import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 客户端登录保活线程，消费者
 */
public class LoginKeepAliveThread implements Runnable{
    private static final Logger log = LoggerFactory.getLogger(LoginKeepAliveThread.class);


    private static final RedisTemplate<String, LoginContent> redisTemplate = CacheFactory.REDIS.instance();

    /**
     * 处理客户端登录保活
     */
    @SuppressWarnings("InfiniteLoopStatement")
    @Override
    public void run() {
        AtomicLong lastBatchSubmitTimeMillis = new AtomicLong(TimeUtil.currentTimeMillis());
        int clientLoginInfoBatchExpireSize = MessageServerContext.serverProperties().getClientLoginInfoBatchExpireSize();
        long clientLoginInfoScheduleTimeInterval = MessageServerContext.serverProperties().getClientLoginInfoScheduleTimeInterval();
        while (true) {
            try {
                // 这里用size 判断虽然会不准，但是没啥问题
                if (MessageServerContext.clientKeepAliveQueue.size() >= clientLoginInfoBatchExpireSize || (!MessageServerContext.clientKeepAliveQueue.isEmpty() && TimeUtil.currentTimeMillis() >= lastBatchSubmitTimeMillis.get() + clientLoginInfoScheduleTimeInterval)) {
                    redisTemplate.executePipelined(new SessionCallback<>() {
                        @SuppressWarnings("unchecked")
                        @Override
                        public <K, V> Object execute(@NotNull RedisOperations<K, V> operations) throws DataAccessException {
                            for (int i = 0; i < clientLoginInfoBatchExpireSize; i++) {
                                LoginClientInfo loginClientInfo = MessageServerContext.clientKeepAliveQueue.poll();
                                if (loginClientInfo != null) {
                                    String comboIdentity = IdentityUtil.generalComboIdentity(loginClientInfo.getAppKey(), loginClientInfo.getIdentity(), loginClientInfo.getDeviceType());
                                    long loginExpireTime = loginClientInfo.getLoginExpireTime();
                                    operations.expire((K) CacheConstant.buildLoginCacheKey(loginClientInfo.getAppKey(), comboIdentity), loginExpireTime, TimeUnit.SECONDS);
                                    operations.opsForZSet().add((K) CacheConstant.buildConnectionsCacheKey(loginClientInfo.getAppKey()), (V) comboIdentity, TimeUtil.currentTimeMillis() + loginExpireTime* MessageConstant.NUMBER_1000);
                                }
                            }
                            return null;
                        }
                    });
                    lastBatchSubmitTimeMillis.set(TimeUtil.currentTimeMillis());
                }
            } catch (Exception e) {
                log.error("客户端登录保活线程异常中断，原因：{}", e.getMessage());
                MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_KEEP_ALIVE_ERROR, "客户端登录保活线程异常中断, 原因" + e.getMessage(), null), MessageEventTypeEnum.EXCEPTION));
            }

        }
    }
}
