package com.ouyunc.message.thread;


import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.message.context.MessageServerContext;
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
            // 这里用size 判断虽然会不准，但是没啥问题
            if (MessageServerContext.clientKeepAliveQueue.size() >= clientLoginInfoBatchExpireSize || (TimeUtil.currentTimeMillis() >= lastBatchSubmitTimeMillis.get() + clientLoginInfoScheduleTimeInterval)) {
                redisTemplate.executePipelined(new SessionCallback<>() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                        for (int i = 0; i < clientLoginInfoBatchExpireSize; i++) {
                            LoginClientInfo loginClientInfo = MessageServerContext.clientKeepAliveQueue.poll();
                            if (loginClientInfo != null) {
                                operations.expire((K) (CacheConstant.OUYUNC + CacheConstant.APP_KEY + loginClientInfo.getAppKey() + CacheConstant.COLON + CacheConstant.LOGIN + CacheConstant.USER + IdentityUtil.generalComboIdentity(loginClientInfo.getAppKey(), loginClientInfo.getIdentity(), loginClientInfo.getDeviceType())), loginClientInfo.getLoginExpireTime(), TimeUnit.SECONDS);
                            }
                        }
                        return null;
                    }
                });
                lastBatchSubmitTimeMillis.set(TimeUtil.currentTimeMillis());
            }
        }
    }
}
