package com.ouyu.cache.constant.enums;

/**
 * @author fangzhenxun
 * @description redis 配置所涉及到的枚举类
 */
public enum RedisEnum {


    /**
     * 单例
     */
    STANDALONE("单例"),

    /**
     * 哨兵
     */
    SENTINEL("哨兵"),

    /**
     * 集群
     */
    CLUSTER("集群");

    /**
     * redis的模式类型
     */
    private String redisModel;

    RedisEnum(String redisModel) {
        this.redisModel = redisModel;
    }

    public String getRedisModel() {
        return redisModel;
    }

    public void setRedisModel(String redisModel) {
        this.redisModel = redisModel;
    }
}
