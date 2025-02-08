package com.ouyunc.message.listener;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.SaveModeEnum;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.ServerStartupEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.schedule.ScheduleTimer;
import com.ouyunc.message.thread.LoginKeepAliveThread;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author fzx
 * @description 服务启动成功事件
 */
public class ServerStartupEventListener implements MessageListener<ServerStartupEvent> {
    private static final Logger log = LoggerFactory.getLogger(ServerStartupEventListener.class);

    /**
     * 服务启动成功事件,
     * @param event
     */
    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        // 判断是否开启客户端登录信息的
        if (MessageServerContext.serverProperties().isClientHeartBeatEnable() && SaveModeEnum.FINITE.equals(MessageServerContext.serverProperties().getClientLoginInfoSaveMode())) {
            Thread clientLoginKeepAliveThread = new Thread(new LoginKeepAliveThread());
            // 设置线程参数
            clientLoginKeepAliveThread.setName("client-login-keep-alive-thread");
            clientLoginKeepAliveThread.setDaemon(true);
            // 开启线程
            clientLoginKeepAliveThread.start();
        }
        // 判断是否启用appKey 连接数的定时刷新,注意：在极端情况下，如果所有服务都宕机，则可能导致连接数的统计不准确，没有及时进行销毁
        if (MessageServerContext.serverProperties().isAppKeyConnectionCountRefreshEnable()) {
            // 启用一个定时任务来做appKey连接数的定时检查刷新
            // 获取redis 实例
            RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();
            // redisson
            RedissonClient redissonClient = CacheFactory.REDISSON.instance();
            // 要删除成员的最小分数， 这个需要给定一个合适的起止时间，比如在程序开始启动的那一天，或者当前时间戳减去一定的时间范围，比如一天，或者一周，或者一个月等。这里用过去一天的时间，这样删除的数据量会比较少，不会影响性能。
            final AtomicLong minScore = new AtomicLong(NumberConstant.NUMBER_0);
            // 调度
            ScheduleTimer.scheduleAtFixedRate("appKey-connection-count-refresh-timer", (taskWrapper) -> {
                // 获取所有appKey
                Set<Object> appKeys = redisTemplate.opsForSet().members(CacheConstant.OUYUNC + CacheConstant.APP_KEYS);
                if (CollectionUtils.isNotEmpty(appKeys)) {
                    // 查询zset score值 小于当前时间戳的 appKey 连接数且不等于-1
                    // 要删除成员的最大分数
                    long maxScore = TimeUtil.currentTimeMillis();
                    if (minScore.get() == NumberConstant.NUMBER_0 || minScore.get() >= maxScore) {
                        minScore.set(maxScore - MessageServerContext.serverProperties().getAppKeyConnectionCountRefreshOffset() * MessageConstant.SECOND_TIMESTAMP);
                    }
                    for (Object appKey : appKeys) {
                        // 加锁,多实例的
                        RLock lock = redissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + appKey);
                        try {
                            // 锁等待 和 锁过期时间
                            if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                                // 分批处理，防止一次删除的数据量过大，造成redis性能问题
                                while (minScore.get() < maxScore) {
                                    // 计算本次删除操作的最大分数边界，确保不超过设定的 maxScore
                                    long currentMaxScore = Math.min(minScore.get() + MessageServerContext.serverProperties().getAppKeyConnectionCountRefreshStep(), maxScore);
                                    // 执行删除操作，返回删除的成员数量
                                    redisTemplate.opsForZSet().removeRangeByScore(CacheConstant.OUYUNC + CacheConstant.CONNECTIONS + CacheConstant.APP_KEY + appKey, minScore.get(), currentMaxScore);
                                    // 更新下一次删除操作的起始分数
                                    minScore.set(currentMaxScore);
                                }
                            }
                        } catch (InterruptedException e) {
                            log.error("appKey-connection-count-refresh-timer 获取锁失败,原因：{}", e.getMessage());
                        } finally {
                            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                                lock.unlock();
                            }
                        }
                    }
                }
            }, MessageServerContext.serverProperties().getAppKeyConnectionCountRefreshInterval(), MessageServerContext.serverProperties().getAppKeyConnectionCountRefreshInterval(), TimeUnit.SECONDS);
        }

    }
}
