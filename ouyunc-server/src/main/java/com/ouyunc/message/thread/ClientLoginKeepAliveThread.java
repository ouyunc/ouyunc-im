package com.ouyunc.message.thread;


import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.message.context.MessageServerContext;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 客户端登录保活线程，消费者
 */
public class ClientLoginKeepAliveThread implements Runnable{

    private static final ExecutorService loginKeepAliveexEcutorService = Executors.newVirtualThreadPerTaskExecutor();

    private static final RedisTemplate<String, LoginContent> redisTemplate = CacheFactory.REDIS.instance();

    /**
     * 处理客户端登录保活
     */
    @SuppressWarnings("InfiniteLoopStatement")
    @Override
    public void run() {
        long lastBatchSubmitTimeMillis = TimeUtil.currentTimeMillis();
        int clientLoginInfoBatchExpireSize = MessageServerContext.serverProperties().getClientLoginInfoBatchExpireSize();
        while (true) {
            // 这里用size 判断虽然会不准，但是没啥问题
            if (MessageServerContext.clientKeepAliveQueue.size() >= clientLoginInfoBatchExpireSize || (lastBatchSubmitTimeMillis >= lastBatchSubmitTimeMillis + 1000)) {
                // 多线程去执行操作redis
                loginKeepAliveexEcutorService.submit(() -> {
                    // 取出一千，有可能取不到，时间到了
                    for (int i = 0; i < clientLoginInfoBatchExpireSize; i++) {
                        LoginClientInfo loginClientInfo = MessageServerContext.clientKeepAliveQueue.poll();
                        if (loginClientInfo != null) {
                            redisTemplate.expire(CacheConstant.OUYUNC + CacheConstant.APP_KEY + loginClientInfo.getAppKey() + CacheConstant.COLON + CacheConstant.LOGIN + CacheConstant.USER + IdentityUtil.generalComboIdentity(loginClientInfo.getAppKey(), loginClientInfo.getIdentity(), loginClientInfo.getDeviceType()), loginClientInfo.getLoginExpireTime(), TimeUnit.SECONDS);
                        }
                    }

                });
            }
        }
    }
}
