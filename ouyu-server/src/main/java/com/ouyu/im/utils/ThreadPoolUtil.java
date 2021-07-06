package com.ouyu.im.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author fangzhenxun
 * @Description 使用双重校验锁实现单例，创建线程池
 */
public class ThreadPoolUtil {
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolUtil.class);


    /**
     * 根获取的是cpu核心线程数也就是计算资源。
     */
    private static final int AVAILABLE_CUPS = Runtime.getRuntime().availableProcessors();
    /**
     * 核心线程数 = CPU核心数 + 1
     */
    private static final int CORE_POOL_SIZE = AVAILABLE_CUPS + 1;
    /**
     * 线程池最大线程数 = CPU核心数 * 2 + 1
     */
    private static final int MAXIMUM_POOL_SIZE = AVAILABLE_CUPS * 2 + 1;
    /**
     * 非核心线程闲置时间 = 超时1s
     */
    private static final long KEEP_ALIVE = 1L;
    /**
     * 队列最大容量,默认integer 最大值
     */
    private static final int QUEUE_CAPACITY = Integer.MAX_VALUE;

    private static volatile ThreadPoolExecutor threadPoolExecutor;

    private ThreadPoolUtil() {
    }


    /**
     * @Author fangzhenxun
     * @Description 获取线程池
     * @param
     * @return java.util.concurrent.ThreadPoolExecutor
     */
    public static ExecutorService getThreadPool() {
        if (threadPoolExecutor == null) {
            synchronized (ThreadPoolUtil.class) {
                if (threadPoolExecutor == null) {
                    logger.info("开始创建线程池：心线程数=>{}, 最大线程数=>{},线程最大空闲时间=>{}", CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE);
                    return new ThreadPoolExecutor(CORE_POOL_SIZE,
                            MAXIMUM_POOL_SIZE,
                            KEEP_ALIVE,
                            TimeUnit.SECONDS, new LinkedBlockingQueue(QUEUE_CAPACITY),
                            new ThreadFactory() {
                                private final AtomicInteger threadNumber = new AtomicInteger(1);
                                private final AtomicInteger poolNumber = new AtomicInteger(1);
                                /**
                                 * @Author fangzhenxun
                                 * @Description 创建线程
                                 */
                                public Thread newThread(Runnable runnable) {
                                    SecurityManager s = System.getSecurityManager();
                                    ThreadGroup group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
                                    Thread t = new Thread(group, runnable,"im-pool-" + poolNumber.getAndIncrement() + "-thread-" + threadNumber.getAndIncrement(),0);
                                    if (t.isDaemon()) {
                                        t.setDaemon(false);
                                    }
                                    if (t.getPriority() != Thread.NORM_PRIORITY) {
                                        t.setPriority(Thread.NORM_PRIORITY);
                                    }
                                    return t;
                                }
                            },
                            new ThreadPoolExecutor.DiscardPolicy());
                }
            }
        }
        return threadPoolExecutor;
    }

}
