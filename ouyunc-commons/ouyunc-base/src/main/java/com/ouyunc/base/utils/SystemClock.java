package com.ouyunc.base.utils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 为高频读场景缓存 {@link System#currentTimeMillis()}：{@link #now()} 仅为 volatile 读，无系统调用。
 * 相对真实时间最多滞后 {@link #REFRESH_PERIOD_MS} 毫秒。使用独立守护线程刷新，不受 {@link com.ouyunc.base.executor.ThreadPoolManager} 重建线程池影响。
 */
public final class SystemClock {

    /**
     * 后台刷新周期（毫秒）。越小越接近实时，调度开销越大。
     */
    private static final long REFRESH_PERIOD_MS = 1L;

    private static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
        private final AtomicInteger n = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "system-clock-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    };

    private static final ScheduledExecutorService REFRESH =
            Executors.newSingleThreadScheduledExecutor(THREAD_FACTORY);

    private static volatile long millis = System.currentTimeMillis();

    static {
        REFRESH.scheduleAtFixedRate(
                () -> millis = System.currentTimeMillis(),
                REFRESH_PERIOD_MS,
                REFRESH_PERIOD_MS,
                TimeUnit.MILLISECONDS);
    }

    private SystemClock() {
    }

    public static long now() {
        return millis;
    }
}
