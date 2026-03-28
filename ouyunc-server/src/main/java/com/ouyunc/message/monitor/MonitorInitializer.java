package com.ouyunc.message.monitor;

import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.MessageEventMulticaster;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.schedule.TimerTaskWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 监控初始化器
 * 在服务启动时注册所有需要监控的缓存实例
 *
 * @author fzx
 */
public class MonitorInitializer {

    private static final Logger log = LoggerFactory.getLogger(MonitorInitializer.class);

    /**
     * 初始化监控
     * 注册所有 Caffeine 缓存实例
     */
    public static void initialize() {
        log.debug("开始初始化资源监控...");

        // 注册 MessageServerContext 中的缓存
        registerCache(MessageServerContext.localLoginClientRegisterTable);
        registerCache(MessageServerContext.localClientInfoCache);
        registerCache(MessageServerContext.messageProcessorCache);
        registerCache(MessageServerContext.messageContentProcessorCache);
        registerCache(MessageServerContext.clusterActiveServerRegistryTableCache);
        registerCache(MessageServerContext.clusterGlobalServerRegistryTableCache);
        registerCache(MessageServerContext.clusterClientCoreChannelPoolCache);
        registerCache(MessageServerContext.clusterClientMissAckTimesCache);
        registerCache(MessageServerContext.luaScriptShaCache);

        // 注册 MessageContext 中的缓存（如果已启用 recordStats）
        registerCache(MessageContext.friendEntityCache);
        registerCache(MessageContext.groupEntityCache);
        registerCache(MessageContext.groupUserEntityCache);
        registerCache(MessageContext.userEntityCache);

        // 注册其他缓存
        registerCache(TimerTaskWrapper.timerTaskCaffeine);

        log.info("资源监控初始化完成，已注册 {} 个缓存", ResourceMonitor.getRegisteredCacheNames().size());
    }

    /**
     * 在事件多播器创建完成后注册 Disruptor 指标拉取（与缓存注册分离，因 multicaster 在 loadEventListener 中创建）。
     */
    public static void registerDisruptorMetrics(MessageEventMulticaster multicaster) {
        ResourceMonitor.registerDisruptorMetrics(multicaster);
    }

    /**
     * 启动定期监控
     * 默认每5分钟输出一次监控报告
     */
    public static void startMonitoring() {
        ResourceMonitor.startMonitoring();
    }

    /**
     * 启动定期监控（自定义周期）
     *
     * @param periodMinutes 监控周期（分钟）
     */
    public static void startMonitoring(int periodMinutes) {
        ResourceMonitor.startMonitoring(periodMinutes, java.util.concurrent.TimeUnit.MINUTES);
    }

    private static void registerCache(Object cache) {
        if (cache != null) {
            ResourceMonitor.registerCache(cache);
        }
    }
}

