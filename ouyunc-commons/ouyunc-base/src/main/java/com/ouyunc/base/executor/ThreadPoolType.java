package com.ouyunc.base.executor;

/**
 * 线程池类型
 */
public enum ThreadPoolType {
    /**
     * 虚拟线程池
     */
    VIRTUAL,

    /**
     * 固定线程池
     */
    FIXED,

    /**
     * 缓存线程池
     */
    CACHED,

    /**
     * 调度线程池
     */
    SCHEDULED,

    /**
     * 单线程池
     */
    SINGLE
}


