package com.ouyu.cache.constant.enums;

/**
 * @Author fangzhenxun
 * @Description: 缓存枚举
 * @Version V1.0
 **/
public enum  CacheEnum {

    /**
     * 分布式缓存 MEMCACHED
     */
    DISTRIBUTED_MEMCACHED,

    /**
     * 分布式缓存 MONGODB
     */
    DISTRIBUTED_MONGODB,

    /**
     * 分布式缓存 REDIS
     */
    DISTRIBUTED_REDIS,

    /**
     * 本地缓存 CAFFEINE
     */
    LOCAL_CAFFEINE,

    /**
     * 本地缓存 ENCACHE
     */
    LOCAL_ENCACHE,

    /**
     * 本地缓存 GUAVACACHE
     */
    LOCAL_GUAVACACHE,

    /**
     * 本地缓存 MAP
     */
    LOCAL_MAP


}
