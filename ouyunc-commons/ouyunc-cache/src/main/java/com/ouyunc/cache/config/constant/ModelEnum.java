package com.ouyunc.cache.config.constant;

/**
 * @author fzx
 * @description redisson 配置所涉及到的枚举类
 */
public enum ModelEnum {


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

    ModelEnum(String redisModel) {
        this.redisModel = redisModel;
    }

    public String getRedisModel() {
        return redisModel;
    }

    public void setRedisModel(String redisModel) {
        this.redisModel = redisModel;
    }
}
