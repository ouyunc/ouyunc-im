package com.ouyunc.message.listener;

import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.core.listener.EventListener;
import com.ouyunc.core.listener.MessageEventListener;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.device.AppKeyDeviceTypeSubscriber;
import com.ouyunc.core.relation.RelationCacheSubscriber;
import com.ouyunc.message.cluster.lease.NodeLeaseKeeper;
import com.ouyunc.message.http.HttpRequestDispatcher;
import com.ouyunc.message.helper.LoginRouteCacheInvalidationBus;
import com.ouyunc.message.monitor.MonitorInitializer;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author fzx
 * @description 服务启动成功事件（Netty bind 成功后）
 */
@EventListener
class ServerStartupEventMessageEventListener implements MessageEventListener<MessageEvent> {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ServerStartupEventMessageEventListener.class);

    private static final AtomicBoolean RUNTIME_RESOURCES_SHUTDOWN_DONE = new AtomicBoolean(false);

    /**
     * 服务启动成功事件,
     */
    @Override
    public EventType type() {
        return MessageEventTypeEnum.SERVER_STARTUP;
    }

    @Override
    public void onEvent(MessageEvent event) {
        HttpRequestDispatcher.logRegisteredHttpRoutesOnStartup();
        // 启动资源监控（不挡首包，bind 成功后再开）
        MonitorInitializer.start();
    }

    /**
     * 释放运行时资源；在 {@link MessageEventTypeEnum#SERVER_STOP} 中同步调用。
     */
    static void shutdownRuntimeResources() {
        if (!RUNTIME_RESOURCES_SHUTDOWN_DONE.compareAndSet(false, true)) {
            return;
        }
        stopResource("device subscriber", AppKeyDeviceTypeSubscriber::stop);
        stopResource("relation subscriber", RelationCacheSubscriber::stop);
        stopResource("login route subscriber", LoginRouteCacheInvalidationBus::stop);
        stopResource("node lease", NodeLeaseKeeper::stop);
    }

    /** 独立清理订阅与租约，单个订阅关闭异常不能使租约仍保持运行。 */
    private static void stopResource(String name, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            log.error("运行时资源关闭失败: {}", name, error);
        }
    }
}
